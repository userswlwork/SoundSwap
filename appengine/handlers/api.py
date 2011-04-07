'''
Created on Apr 3, 2011

@author: peterjdolan
'''

import datetime
import logging

from handlers import common
from models import recording

from google.appengine.ext import blobstore
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import blobstore_handlers


class GetBlobUploadHandler(webapp.RequestHandler):
  def get(self):
    upload_url = blobstore.create_upload_url(common._UPLOAD_SOUND_BASE_PATH)
    self.redirect(upload_url)


class GetRecordingHandler(blobstore_handlers.BlobstoreDownloadHandler):
  def get(self):
    device_id = self.request.get(common._DEVICE_ID_URI_KEY)
    if device_id is not None and device_id == "null":
      device_id = None
    
    for attempt in xrange(100):
      logging.debug("Fetch attempt %d" % attempt)
      recordings = recording.Recording.all()
      
      filter = ">"
      order = ""
      if attempt / 2 == 1:
        filter = "<"
        order = "-"
      random_number = common.GetRandomNumber()
      logging.debug("Ordering by random number: %s %d" % 
                    (filter, random_number))
      recordings.filter("random_number %s " % filter, random_number)
      recordings.order("%srandom_number" % order)
      
      for record in recordings.fetch(limit=1000):
        if device_id is not None and record.device_id == device_id:
          logging.debug("Skipping record due to device id.")
          continue
        self.response.headers.add_header("X-SoundSwap-Filename", 
                                         record.blob.filename)
        self.send_blob(record.blob)
        return

    # TODO: redirect to a standard file
    logging.error("No sounds available for request.")
    self.redirect("/")


class SoundUploadHandler(blobstore_handlers.BlobstoreUploadHandler):
  def post(self):
    uploads = self.get_uploads()
    assert uploads is not None
    assert len(uploads) == 1
    assert uploads[0] is not None
    
    upload = uploads[0]
    assert upload is not None
    
    upload_key = upload.key()
    assert upload_key is not None

    device_id = None
    time_ms = None
    latE6 = None
    lonE6 = None
    
    if self.request.get("from_web") == "True":
      device_id = self.request.get("device_id")
      time_ms = int(self.request.get("time_ms"))
      latE6 = int(self.request.get("latE6"))
      lonE6 = int(self.request.get("lonE6"))
    else:
      # Sample filename: 1300058598218_37765137_-122450695.wav.zip
      filename = upload.filename
      parts = filename.split(".")[0].split("_")
      device_id = parts[0]
      time_ms = int(parts[1])
      latE6 = int(parts[2])
      lonE6 = int(parts[3])
    
    assert device_id is not None
    assert time_ms is not None
    assert lonE6 is not None
    assert latE6 is not None
    
    location = db.GeoPt(latE6 / 1E6, lonE6 / 1E6)
    created_time = datetime.datetime.fromtimestamp((int) (time_ms/1000))
    
    record = recording.Recording(device_id = device_id,
                                 location = location,
                                 created_time = created_time,
                                 blob = upload_key,
                                 random_number = common.GetRandomNumber())
    record.put()
    
    redirect_uri = ("%s?%s=%s" % 
                    (common._GET_SOUND_PATH, 
                     common._DEVICE_ID_URI_KEY, 
                     device_id))
    self.redirect(redirect_uri)