# Textbender

Bend Android text to your whim.

*This readme is a bit anemic as this is app is still immature.*

## Overview

Textbender is an Android app which lets you shuffle text from various sources to
various sources. In more concrete terms, it lets you do things like turn all
text on your screen into buttons you can press to open them in a dictionary like
[Yomichan](https://foosoft.net/projects/yomichan/).

## Note on Yomichan Support

The yomichan integration is pretty flaky but works mostly reliable on my device.
It has the following requirements:

- Textbender's accessibility service is enabled
- Kiwi Browser and the Yomichan extension are both installed
- Kiwi Browser is *open* with at least one tab.

## Development

The Textbender dev environment is provided as a Nix flake and is very easy to
build with [Nix](https://nixos.org/).

``` shell
nix develop
./gradlew installDebug
```

It's really that simple. (Assuming you have Nix set up correctly.)
