'''
Created on Apr 3, 2011

@author: peterjdolan
'''

import os
import random

from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template


_UPLOAD_FORM_REDIRECT_PATH = "/api/sound/upload_form_redirect"
_UPLOAD_SOUND_BASE_PATH = "/api/sound/upload"
_GET_SOUND_PATH = "/api/sound"

_DEVICE_ID_URI_KEY = "device_id"

def GetRandomNumber():
  """
  Get a random number.  This method's return value's range must not
  change, for datastore backwards-compatibility.
  """
  return random.randint(-2**32, 2**32)


class HtmlHandler(webapp.RequestHandler):
    
  def OutputTemplate(self, dict, template_name):
    user = users.get_current_user()
    dict["user"] = user
    dict["logged_in"] = user is not None
    
    current_url = self.request.url
    dict["login_url"] = users.create_login_url(current_url)
    dict["logout_url"] = users.create_logout_url(current_url)
    path = os.path.join(os.path.dirname(__file__),
                        '../templates', 
                        template_name)
    self.response.out.write(template.render(path, dict))
