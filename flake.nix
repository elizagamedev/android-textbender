# This flake is based on the lovely example here:
# https://github.com/fcitx5-android/fcitx5-android/blob/master/flake.nix
{
  description = "Dev shell flake for textbender";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  inputs.flake-compat = {
    url = "github:edolstra/flake-compat";
    flake = false;
  };

  outputs = { self, nixpkgs, ... }:
    let
      pkgs = import nixpkgs {
        system = "x86_64-linux";
        config.android_sdk.accept_license = true;
        config.allowUnfree = true;
        overlays = [ self.overlays.default ];
      };
    in
    with pkgs;
    with textbender-android-sdk;
    {
      devShells.x86_64-linux.default = mkShell {
        buildInputs = [
          androidComposition.androidsdk
          androidStudioPackages.beta
        ];
        ANDROID_SDK_ROOT =
          "${androidComposition.androidsdk}/libexec/android-sdk";
        GRADLE_OPTS =
          "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidComposition.androidsdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2";
        JAVA_HOME = "${jdk11}";
        shellHook = ''
          echo sdk.dir=$ANDROID_SDK_ROOT > local.properties
        '';
      };
    } // {
      overlays.default = final: prev: {
        textbender-android-sdk = rec {
          buildToolsVersion = "33.0.0";
          androidComposition = prev.androidenv.composeAndroidPackages {
            platformToolsVersion = "33.0.2";
            buildToolsVersions = [ buildToolsVersion ];
            platformVersions = [ "33" ];
            abiVersions = [ "arm64-v8a" "armeabi-v7a" ];
            includeNDK = false;
            includeEmulator = false;
            useGoogleAPIs = false;
          };
        };
      };
    };
}
