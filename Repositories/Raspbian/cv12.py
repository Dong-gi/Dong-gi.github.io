import numpy as np
import cv2


img = cv2.imread('start05-2.jpg')
kernel = np.ones((3, 3), np.uint8)
cv2.imshow('erode', cv2.erode(img, kernel, iterations = 1))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('dilate', cv2.dilate(img, kernel, iterations = 1))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('open', cv2.morphologyEx(img, cv2.MORPH_OPEN, kernel))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('close', cv2.morphologyEx(img, cv2.MORPH_CLOSE, kernel))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('gradient', cv2.morphologyEx(img, cv2.MORPH_GRADIENT, kernel))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('top hat', cv2.morphologyEx(img, cv2.MORPH_TOPHAT, kernel))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('black hat', cv2.morphologyEx(img, cv2.MORPH_BLACKHAT, kernel))
cv2.waitKey(0)
cv2.destroyAllWindows()
