LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := yaml
LOCAL_SRC_FILES := $(wildcard *.c)
include $(BUILD_STATIC_LIBRARY)