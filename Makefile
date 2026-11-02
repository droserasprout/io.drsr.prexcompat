JAVA_HOME := /usr/lib/jvm/java-21-openjdk
ANDROID_SDK_ROOT := $(HOME)/Android/Sdk
ANDROID_NDK_HOME ?= /opt/android-ndk
NDK_HOST_TAG ?= linux-x86_64
NDK_BIN := $(ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/$(NDK_HOST_TAG)/bin
MAGISK_SRC := art-view-sync
MAGISK_STAGE := build/art-view-sync
MAGISK_ZIP := build/art-view-sync.zip

unexport ANDROID_HOME

.PHONY: build install release magisk lint clean

build:
	./gradlew assembleDebug

install:
	./gradlew installDebug

release:
	./gradlew assembleRelease

magisk:
	mkdir -p $(MAGISK_STAGE)/bin
	"$(NDK_BIN)/clang" --target=aarch64-linux-android28 -static -O2 -Wall -Wextra -Werror \
		-o $(MAGISK_STAGE)/bin/artviewsync $(MAGISK_SRC)/src/artviewsync.c
	"$(NDK_BIN)/llvm-strip" $(MAGISK_STAGE)/bin/artviewsync
	cp $(MAGISK_SRC)/module.prop $(MAGISK_SRC)/service.sh $(MAGISK_SRC)/uninstall.sh \
		$(MAGISK_SRC)/customize.sh $(MAGISK_STAGE)/
	rm -f $(MAGISK_ZIP)
	cd $(MAGISK_STAGE) && zip -9 ../art-view-sync.zip \
		module.prop service.sh uninstall.sh customize.sh bin/artviewsync

lint:
	./gradlew ktlintCheck detekt

clean:
	./gradlew clean
	rm -rf $(MAGISK_STAGE) $(MAGISK_ZIP)
