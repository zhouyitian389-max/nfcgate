cmake_minimum_required(VERSION 3.18)
project(nfcgate_fuzz)

add_executable(emv_fuzzer emv_fuzzer.cpp)
add_executable(mifare_fuzzer mifare_fuzzer.cpp)
add_executable(apdu_fuzzer apdu_fuzzer.cpp)
