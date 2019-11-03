import numpy as np
import cv2


img = cv2.imread('start05-2.jpg')
cv2.imshow('low', cv2.pyrDown(img))
cv2.imshow('high', cv2.pyrUp(img))
cv2.waitKey(0)
