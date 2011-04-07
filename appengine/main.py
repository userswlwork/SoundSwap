import logging

from handlers import api
from handlers import common
from handlers import site

from google.appengine.ext import webapp
from google.appengine.ext.webapp import util


def main():
  application = webapp.WSGIApplication(
      [(common._UPLOAD_FORM_REDIRECT_PATH, api.GetBlobUploadHandler),
       (common._UPLOAD_SOUND_BASE_PATH, api.SoundUploadHandler),
       (common._GET_SOUND_PATH, api.GetRecordingHandler),
       ("/", site.HomepageHandler),
       ("/sound/upload", site.UploadHandler),
       ("/sound/list", site.ListHandler),
       ],
      debug=True)
  util.run_wsgi_app(application)


if __name__ == '__main__':
  main()
