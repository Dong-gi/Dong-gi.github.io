import cv2
import numpy as np

img = cv2.imread('start05-2.jpg')
cv2.imshow('img', img)
hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

blue = np.uint8([[[255, 0, 0]]])
hsvBlue = cv2.cvtColor(blue, cv2.COLOR_BGR2HSV)
hue, saturation, value = hsvBlue[0, 0]
lowBlue = np.array([hue-10, 100, 100])
highBlue = np.array([hue+10, 255, 255])
mask = cv2.bitwise_not(cv2.inRange(hsv, lowBlue, highBlue))
cv2.imshow('mask', mask)

img = cv2.bitwise_and(img, img, mask = mask)
cv2.imshow('result', img)
cv2.waitKey(0)
