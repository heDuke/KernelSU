//! Original streaming implementation of the public SHA-256 algorithm.
use std::io::{self, Read};

pub struct Digest {
    words: [u32; 8],
    tail: [u8; 64],
    used: usize,
    bytes: u64,
}

impl Digest {
    pub fn new() -> Self {
        Self {
            words: [
                0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab,
                0x5be0cd19,
            ],
            tail: [0; 64],
            used: 0,
            bytes: 0,
        }
    }

    pub fn add(&mut self, input: &[u8]) {
        self.bytes = self.bytes.wrapping_add(input.len() as u64);
        let mut rest = input;
        while !rest.is_empty() {
            let take = (64 - self.used).min(rest.len());
            self.tail[self.used..self.used + take].copy_from_slice(&rest[..take]);
            self.used += take;
            rest = &rest[take..];
            if self.used == 64 {
                self.compress();
                self.used = 0;
            }
        }
    }

    fn compress(&mut self) {
        const ROUND: [u32; 64] = [
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4,
            0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe,
            0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f,
            0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc,
            0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
            0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116,
            0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7,
            0xc67178f2,
        ];
        let mut schedule = [0u32; 64];
        for (i, chunk) in self.tail.as_chunks::<4>().0.iter().enumerate() {
            schedule[i] = u32::from_be_bytes(*chunk);
        }
        for i in 16..64 {
            let x = schedule[i - 15];
            let y = schedule[i - 2];
            let low = x.rotate_right(7) ^ x.rotate_right(18) ^ (x >> 3);
            let high = y.rotate_right(17) ^ y.rotate_right(19) ^ (y >> 10);
            schedule[i] = schedule[i - 16]
                .wrapping_add(low)
                .wrapping_add(schedule[i - 7])
                .wrapping_add(high);
        }
        let mut working = self.words;
        for i in 0..64 {
            let [a, b, c, d, e, f, g, h] = working;
            let first = h
                .wrapping_add(e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25))
                .wrapping_add((e & f) ^ (!e & g))
                .wrapping_add(ROUND[i])
                .wrapping_add(schedule[i]);
            let second = (a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22))
                .wrapping_add((a & b) ^ (a & c) ^ (b & c));
            working = [
                first.wrapping_add(second),
                a,
                b,
                c,
                d.wrapping_add(first),
                e,
                f,
                g,
            ];
        }
        for (state, value) in self.words.iter_mut().zip(working) {
            *state = state.wrapping_add(value);
        }
    }

    pub fn finish(mut self) -> String {
        let bits = self.bytes.wrapping_mul(8);
        self.add(&[0x80]);
        while self.used != 56 {
            self.add(&[0]);
        }
        self.add(&bits.to_be_bytes());
        self.words
            .iter()
            .map(|value| format!("{value:08x}"))
            .collect()
    }
}

pub fn bytes(input: &[u8]) -> String {
    let mut state = Digest::new();
    state.add(input);
    state.finish()
}

pub fn reader(mut input: impl Read) -> io::Result<String> {
    let mut state = Digest::new();
    let mut buffer = [0u8; 16384];
    loop {
        match input.read(&mut buffer) {
            Ok(0) => return Ok(state.finish()),
            Ok(count) => state.add(&buffer[..count]),
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(error),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn standard_vectors_and_streaming_boundaries() {
        assert_eq!(
            bytes(b""),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
        assert_eq!(
            bytes(b"abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
        assert_eq!(
            bytes(b"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
        );
        let long = vec![b'a'; 1_000_000];
        assert_eq!(
            bytes(&long),
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"
        );
        let mut chunked = Digest::new();
        for piece in long.chunks(67) {
            chunked.add(piece);
        }
        assert_eq!(chunked.finish(), bytes(&long));
        assert_eq!(reader(long.as_slice()).unwrap(), bytes(&long));
    }
}
