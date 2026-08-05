import numpy as np
import cv2

img = cv2.imread('start05.jpg')
print(img.shape)
print(img[100, 100])
print(img.size)
print(img.dtype)

region = img[0:100, 0:200]
img[100:200, 200:400] = region

cv2.imshow('image', img)
cv2.waitKey(0)

blue = img[:, :, 0]
cv2.imshow('blue', blue)
cv2.waitKey(0)

img[:, :, 0] = 0
cv2.imshow('no-blue', img)
cv2.waitKey(0)

frame = cv2.copyMakeBorder(img, 10, 20, 30, 40, cv2.BORDER_CONSTANT, value=(255, 0, 0))
cv2.imshow('frame', frame)
cv2.waitKey(0)
