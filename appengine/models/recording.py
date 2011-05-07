'''
Created on Apr 3, 2011

@author: peterjdolan
'''

import random

from google.appengine.ext import blobstore
from google.appengine.ext import db


class Recording(db.Model):
  # An id given to this recording by the client.  This id is unique to
  # recordings created by the user.
  client_key = db.IntegerProperty()
  
  user = db.UserProperty(auto_current_user_add=True)
  location = db.GeoPtProperty()
  file_parts = db.ListProperty(item_type=blobstore.BlobKey)
  created_time = db.DateTimeProperty()
  
  # Random 64-bit number, generated via #GetRandomNumber
  random_number = db.IntegerProperty()