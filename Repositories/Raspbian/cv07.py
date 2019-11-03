import numpy as np
import cv2

img1 = cv2.imread('start05.jpg')
img2 = cv2.imread('start05-2.jpg')

print(cv2.add(np.uint8([250]), np.uint8([20])))

# dst = a*img1 + b*img2 + c
cv2.imshow('Image Blending', cv2.addWeighted(img1, 0.3, img2, 0.7, 0))
cv2.waitKey(0)

# put logo on top-left
logo = cv2.imread('logo.jpg', cv2.IMREAD_COLOR)
rows, cols, channels = logo.shape
roi = img2[0:rows, 0:cols]

# create a mask of logo
logoGray = cv2.cvtColor(logo, cv2.COLOR_BGR2GRAY)
ret, mask = cv2.threshold(logoGray, 200, 255, cv2.THRESH_BINARY)
maskInv = cv2.bitwise_not(mask)

# black-out the area of logo in ROI
img2Back = cv2.bitwise_and(roi, roi, mask = mask)

# pure logo
logoFore = cv2.bitwise_and(logo, logo, mask = maskInv)

# put logo
dst = cv2.add(img2Back, logoFore)
img2[0:rows, 0:cols] = dst

cv2.imshow('result', img2)
cv2.waitKey(0)
