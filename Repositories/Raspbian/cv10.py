import cv2
import numpy as np

img = cv2.imread('start05-2.jpg')

cv2.imshow('img', cv2.resize(img, None, fx=1.5, fy=0.5))
cv2.waitKey(0)

mat = np.float32([[1, 0, 100], [0, 1, 50]])
r, c, _ = img.shape
cv2.imshow('img', cv2.warpAffine(img, mat, (c, r)))
cv2.waitKey(0)

mat = cv2.getRotationMatrix2D((0, 0), 45, 1)
cv2.imshow('img', cv2.warpAffine(img, mat, (c, r)))
cv2.waitKey(0)

before = np.float32([[0, 0], [100, 100], [100, 200]])
after = np.float32([[200, 100], [200, 200], [100, 50]])
mat = cv2.getAffineTransform(before, after)
cv2.imshow('img', cv2.warpAffine(img, mat, (c, r)))
cv2.waitKey(0)

before = np.float32([[50, 60], [370, 50], [30, 390], [390, 390]])
after = np.float32([[0, 0], [300, 0], [0, 300], [300, 300]])
mat = cv2.getPerspectiveTransform(before, after)
cv2.imshow('img', cv2.warpPerspective(img, mat, (c, r)))
cv2.waitKey(0)
