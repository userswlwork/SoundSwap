'''
Created on Apr 3, 2011

@author: peterjdolan
'''

from handlers import common
from models import recording

from google.appengine.api import users
from google.appengine.ext import blobstore


class AuthenticatedHandler(common.HtmlHandler):

  def Authenticated(self):
    return users.get_current_user() is not None
  
  def Authenticate(self):
    self.redirect(users.create_login_url(self.request.url))
    

class HomepageHandler(common.HtmlHandler):
  
  def get(self):
    self.OutputTemplate({}, "home.html")


class UploadHandler(AuthenticatedHandler):
  
  def get(self):
    if not self.Authenticated():
      self.Authenticate()
      return
    
    upload_url = blobstore.create_upload_url(common._UPLOAD_SOUND_BASE_PATH)
    self.OutputTemplate({"upload_url": upload_url}, "upload.html")


class ListHandler(AuthenticatedHandler):
  
  def get(self):
    if not self.Authenticated():
      self.Authenticate()
      return
    
    user = users.get_current_user()
    recordings = recording.Recording.all().filter("user != ", user.email())
    
    self.OutputTemplate({"recordings": recordings}, "list.html")