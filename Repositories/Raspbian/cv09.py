import cv2
import numpy as np

img = cv2.imread('start05-2.jpg', cv2.IMREAD_GRAYSCALE)
cv2.imshow('original', img)

for mode in [mode for mode in dir(cv2) if mode.startswith('THRESH_')]:
    if mode == 'THRESH_OTSU':
        continue
    _, thresh = cv2.threshold(img, 127, 255, getattr(cv2, mode))
    cv2.imshow(mode, thresh)
    if mode != 'THRESH_TRIANGLE':
        _, thresh = cv2.threshold(img, 0, 255, getattr(cv2, mode) + cv2.THRESH_OTSU)
        cv2.imshow(mode + '+OTSU', thresh)
    cv2.waitKey(0)
    cv2.destroyWindow(mode)
    cv2.destroyWindow(mode + '+OTSU')

cv2.imshow('Adaptive Mean', cv2.adaptiveThreshold(img, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 11, 2))
cv2.imshow('Adaptive Gaussian', cv2.adaptiveThreshold(img, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2))
cv2.waitKey(0)
