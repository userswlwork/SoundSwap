'''
Created on Apr 3, 2011

@author: peterjdolan
'''

import random

from google.appengine.ext import blobstore
from google.appengine.ext import db


class Recording(db.Model):
  location = db.GeoPtProperty()
  device_id = db.StringProperty()
  blob = blobstore.BlobReferenceProperty()
  created_time = db.DateTimeProperty()
  
  # Random 64-bit number, generated via #GetRandomNumber
  random_number = db.IntegerProperty()