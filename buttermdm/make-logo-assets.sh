#!/bin/bash
# Generates the web console images from the ButterMDM logo (buttermdm/logo/buttermdm-logo.png; the vector original is
# buttermdm-logo.svg). Requires ImageMagick 7 ("magick"). Run from the repository root after changing the logo.
set -e
LOGO=buttermdm/logo/buttermdm-logo.png
PNG="-strip -define png:exclude-chunks=date,time"
# The logo is wide (2301x720): square icons show it centred on a transparent square of its width
SQUARE="-background none -gravity center -extent 2301x2301"

# Logo of the login and sign-up pages (images/logo.png, served by /rest/public/logo)
magick $LOGO -resize x80 $PNG server/src/main/webapp/images/logo.png

# Browser tab icon, several sizes
magick $LOGO $SQUARE -define icon:auto-resize=256,128,96,72,64,48,32,24,16 server/src/main/webapp/images/favicon.ico

# Swagger UI (API documentation) tab icons
for s in 16 32; do
    magick $LOGO $SQUARE -resize ${s}x${s} $PNG swagger/ui/favicon-${s}x${s}.png
done
