#!/usr/bin/env python3
"""Run observer lifecycle failure cases without loading an LKM.

Compile the actual implementation with controlled kernel-operation doubles.
This verifies cleanup/serialization, not kernel ABI or device behavior.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = r'''
#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
typedef uint32_t u32;
#define FS_CREATE 1
#define FS_MOVE 2
#define FS_EVENT_ON_CHILD 4
#define FS_ISDIR 8
#define GFP_KERNEL 0
#define LOOKUP_FOLLOW 0
#define __exit
#define KERNEL_VERSION(a,b,c) (((a)<<16)|((b)<<8)|(c))
#define LINUX_VERSION_CODE KERNEL_VERSION(6,1,166)
#define DEFINE_MUTEX(name) pthread_mutex_t name = PTHREAD_MUTEX_INITIALIZER
#define mutex_lock(p) assert(pthread_mutex_lock(p) == 0)
#define mutex_unlock(p) assert(pthread_mutex_unlock(p) == 0)
#define IS_ERR(p) ((intptr_t)(p) < 0 && (intptr_t)(p) >= -4095)
#define PTR_ERR(p) ((int)(intptr_t)(p))
#define pr_info(...) ((void)0)
#define pr_err(...) ((void)0)
struct inode { int refs; };
struct dentry { struct inode *inode; };
struct path { struct dentry *dentry; void *mnt; };
struct qstr { unsigned len; const char *name; };
struct fsnotify_group { int alive; };
struct fsnotify_mark { u32 mask; int attached; };
struct fsnotify_ops {
 int (*handle_inode_event)(struct fsnotify_mark *,u32,struct inode *,struct inode *,const struct qstr *,u32);
};
static struct inode node;
static struct dentry dent = { &node };
static int fault, groups, marks, paths;
static void track_throne(bool value) { (void)value; }
static int kern_path(const char *name,int flags,struct path *path) {
 (void)name; (void)flags;
 if (fault == 2) return -ENOENT;
 path->dentry = &dent; paths++; return 0;
}
static struct inode *d_inode(struct dentry *d) { return d->inode; }
static void ihold(struct inode *i) { i->refs++; }
static void iput(struct inode *i) { assert(i && i->refs > 0); i->refs--; }
static void path_put(struct path *p) { assert(p->dentry && paths > 0); paths--; }
static void *kzalloc(size_t size,int flags) { (void)flags; return fault == 3 ? NULL : calloc(1,size); }
static struct fsnotify_group *fsnotify_alloc_group(const struct fsnotify_ops *ops,int flags) {
 (void)ops; (void)flags;
 if (fault == 1) return (void *)(intptr_t)-ENOMEM;
 struct fsnotify_group *p = calloc(1,sizeof(*p)); assert(p); p->alive = 1; groups++; return p;
}
static void fsnotify_put_group(struct fsnotify_group *p) {
 assert(p && !IS_ERR(p) && p->alive); p->alive = 0; groups--; free(p);
}
static void fsnotify_init_mark(struct fsnotify_mark *m,struct fsnotify_group *p) {
 assert(m && p && p->alive); marks++;
}
static int fsnotify_add_inode_mark(struct fsnotify_mark *m,struct inode *i,int flags) {
 (void)i; (void)flags; if (fault == 4) return -ENOMEM; m->attached = 1; return 0;
}
static void fsnotify_destroy_mark(struct fsnotify_mark *m,struct fsnotify_group *p) {
 assert(m->attached && p->alive); m->attached = 0;
}
static void fsnotify_put_mark(struct fsnotify_mark *m) { assert(m && !m->attached); marks--; free(m); }
'''
CASES = r'''
static void balanced(void) { assert(groups == 0 && marks == 0 && paths == 0 && node.refs == 0 && g == NULL); }
static void *cycle(void *unused) {
 (void)unused;
 for (int i=0;i<200;i++) { assert(ksu_observer_init() == 0); ksu_observer_exit(); }
 return NULL;
}
int main(void) {
 ksu_observer_exit(); balanced();
 const int expected[] = {0,-ENOMEM,-ENOENT,-ENOMEM,-ENOMEM};
 for (fault=1;fault<=4;fault++) {
  assert(ksu_observer_init() == expected[fault]);
  balanced(); ksu_observer_exit(); balanced();
  assert(g_watch.kpath.dentry == NULL && g_watch.inode == NULL && g_watch.mark == NULL);
 }
 fault = 0;
 assert(ksu_observer_init() == 0); assert(ksu_observer_init() == 0);
 assert(groups == 1 && marks == 1 && paths == 1 && node.refs == 1);
 ksu_observer_exit(); ksu_observer_exit(); balanced();
 pthread_t first,second;
 assert(pthread_create(&first,NULL,cycle,NULL) == 0);
 assert(pthread_create(&second,NULL,cycle,NULL) == 0);
 assert(pthread_join(first,NULL) == 0 && pthread_join(second,NULL) == 0);
 ksu_observer_exit(); balanced();
 puts("PASS: early unload, group/path/mark failures, repeat init/exit, concurrent lifecycle");
}
'''

source = (ROOT / 'kernel/manager/pkg_observer.c').read_text()
source = '\n'.join(line for line in source.splitlines() if not line.startswith('#include'))
with tempfile.TemporaryDirectory(prefix='ksu-observer-') as directory:
    c_file = Path(directory) / 'observer.c'
    binary = Path(directory) / 'observer-test'
    c_file.write_text(STUBS + source + CASES)
    subprocess.run([os.environ.get('CC', 'cc'), '-std=c11', '-pthread', '-Wall', '-Wextra',
                    '-Werror', '-Wno-unused-parameter', str(c_file), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
