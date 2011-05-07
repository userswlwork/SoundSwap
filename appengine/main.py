import logging

from handlers import api
from handlers import common
from handlers import site

from google.appengine.ext import webapp
from google.appengine.ext.webapp import util


def main():
  application = webapp.WSGIApplication(
      [('/', site.HomepageHandler),
       ('/api/sound/list', api.GetMyRecordingsHandler),
       ('/api/sound/new?client_key=([^&]+)&created_time_ms=([0-9]+)&latE6=([\-0-9]+)&lonE6=([\-0-9]+)', api.CreateRecordingHandler),
       ('/api/sound/upload_redirect?client_key=([^&]+)', api.FilePartRedirectHandler),
       ('/api/sound/upload_finished?client_key=([^&]+)', api.FilePartFinishedHandler),
       ('/api/sound', api.GetRecordingHandler),
       ('/api/sound/([^/]+)', api.GetRecordingBlobHandler),
       ('/sound/upload?recording_id=([\d]+)', site.UploadHandler),
       ('/sound/list', site.ListHandler),
       ],
      debug=True)
  util.run_wsgi_app(application)


if __name__ == '__main__':
  main()
