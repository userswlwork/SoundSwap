import logging

from handlers import api
from handlers import common
from handlers import site

from google.appengine.ext import webapp
from google.appengine.ext.webapp import util


def main():
  application = webapp.WSGIApplication(
      [("/", site.HomepageHandler),
       ("/api/sound", api.GetRecordingHandler),
       ("/api/sound/list", api.GetMyRecordingsHandler),
       ("/api/sound/upload_form_redirect", api.GetBlobUploadHandler),
       (common._UPLOAD_SOUND_BASE_PATH, api.SoundUploadHandler),
       ("/sound/upload", site.UploadHandler),
       ("/sound/list", site.ListHandler),
       ],
      debug=True)
  util.run_wsgi_app(application)


if __name__ == '__main__':
  main()
