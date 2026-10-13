package me.weishu.kernelsu.data.model

enum class ModuleCompatibility { UNKNOWN, INCOMPATIBLE }

// API range alone cannot establish KernelSU compatibility.
fun moduleCompatibility(api: Int, minApi: Int?, maxApi: Int?): ModuleCompatibility =
    if ((minApi != null && api < minApi) || (maxApi != null && api > maxApi)) {
        ModuleCompatibility.INCOMPATIBLE
    } else {
        ModuleCompatibility.UNKNOWN
    }
