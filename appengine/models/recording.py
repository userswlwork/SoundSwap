'''
Created on Apr 3, 2011

@author: peterjdolan
'''

import random

from google.appengine.ext import blobstore
from google.appengine.ext import db


class Recording(db.Model):
  user = db.UserProperty(auto_current_user_add=True)
  location = db.GeoPtProperty()
  blob = blobstore.BlobReferenceProperty()
  created_time = db.DateTimeProperty()
  
  # Random 64-bit number, generated via #GetRandomNumber
  random_number = db.IntegerProperty()