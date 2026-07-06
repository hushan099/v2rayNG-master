LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := lwip
LOCAL_SRC_FILES := $(wildcard *.c)
include $(BUILD_STATIC_LIBRARY)