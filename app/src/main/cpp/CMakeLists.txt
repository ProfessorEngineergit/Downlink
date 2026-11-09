cmake_minimum_required(VERSION 3.22)
project(downlink_sdr C)

# libusb (LGPL-2.1) for Android: devices are opened by the app and wrapped by file descriptor
add_library(usb1 STATIC
    libusb/core.c libusb/descriptor.c libusb/hotplug.c libusb/io.c libusb/strerror.c libusb/sync.c
    libusb/os/linux_usbfs.c libusb/os/linux_netlink.c libusb/os/events_posix.c libusb/os/threads_posix.c)
target_include_directories(usb1 PUBLIC libusb PRIVATE libusb/os)
target_compile_options(usb1 PRIVATE -w)

# librtlsdr (GPL-2.0-or-later), Osmocom
add_library(rtlsdr STATIC
    rtlsdr/librtlsdr.c rtlsdr/tuner_e4k.c rtlsdr/tuner_fc0012.c rtlsdr/tuner_fc0013.c rtlsdr/tuner_fc2580.c rtlsdr/tuner_r82xx.c)
target_include_directories(rtlsdr PUBLIC rtlsdr/include)
target_compile_definitions(rtlsdr PRIVATE DETACH_KERNEL_DRIVER=1 rtlsdr_STATIC)
target_compile_options(rtlsdr PRIVATE -w)
target_link_libraries(rtlsdr usb1)

add_library(downlinksdr SHARED rtl_jni.c)
target_link_libraries(downlinksdr rtlsdr usb1 log)
