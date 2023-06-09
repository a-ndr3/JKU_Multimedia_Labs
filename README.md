# Multimediasystem Text Recognition

## Group Members

| Student ID    | First Name  | Last Name      |
|---------------|-------------|----------------|
| k12239568	    | Andrei	    | Blokhin        |
| k11803216     | Daniel      | Morawetz       |
| k11908882     | Jan         | Rendel         |
| k1557680      | Esmeralda   | Balla          |

## Overview

In this project we are implementing a demo Android app, to show the working of
image-to-text recognition systems.

We will use the [Google ML Kit](https://developers.google.com/ml-kit/vision/text-recognition) for text
recognition and implement several techniques to improve the recognition quality. The first technique that will be implemented is a set of various filters. We want to try different filters and their combinations to find an optimal way to make the process of text recognition faster and more effective. In addition to core filters such as contrast, median, blur and binary filters we will also explore optional features that can further increase the rate of text recognition on photos, if time allows. Some of these features include live image processing, paper outline detection, brightness filter using the HSV color model, adaptive scaling, Gaussian filter, and diffusion.

## Tech stack
- Android Studio
- Kotlin
- ML Kit

## TODOs

- [ ] create app
- [ ] create testing images
- [+] implement an averaging filter
- [+] implement a binary filter
- [+] implement a contrast filter
- [+] implement a median filter
- [+] added BlackWhite filter
- [+] added sharpening(convolution) filter
- [+] added edge-sharpening filter
- [ ] optional: implement live image processing
- [ ] optional: mark the outline of the paper, to deskew the image
- [+] optional: implement a brightnes filter using the HSV color model
- [+] using HSV get two more filtering options: hue and saturation
- [ ] optional: implement adaptive scaling
- [ ] optional: implement diffusion
