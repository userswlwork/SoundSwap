'''
Created on Apr 3, 2011

@author: peterjdolan
'''

import datetime
import logging
import urllib

from handlers import common
from models import recording

from google.appengine.api import users
from google.appengine.ext import blobstore
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import blobstore_handlers


class GetRecordingHandler(blobstore_handlers.BlobstoreDownloadHandler):
  def get(self):
    user = users.get_current_user()
    
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
        if record.user == user:
          logging.debug("Skipping record due to user id.")
          continue
        self.response.headers.add_header("X-SoundSwap-Filename", 
                                         record.blob.filename)
        self.send_blob(record.blob)
        return

    # TODO: redirect to a standard file
    logging.error("No sounds available for request.")
    self.redirect("/")


class GetRecordingBlobHandler(blobstore_handlers.BlobstoreDownloadHandler):
  def get(self, resource):
    resource = str(urllib.unquote(resource))
    blob_info = blobstore.BlobInfo.get(resource)
    
    content_type = blob_info.content_type
    if blob_info.content_type == "application/octet-stream":
      if blob_info.filename.endswith("wav"):
        content_type = "audio/wav"
    self.send_blob(blob_info, content_type=content_type)
    

class FilePartRedirectHandler(blobstore_handlers.BlobstoreUploadHandler):
  def post(self, client_key):
    user = users.get_current_user()
    recording = recording.Recording.all() \
        .filter("client_key = ", client_key) \
        .get()
    if recording is None or recording.user != user:
      self.error(401)
      return
    
    upload_url = blobstore.create_upload_url(
        "/api/sound/upload_finished?client_key=%s" % client_key)
    self.redirect(upload_url)


class FilePartFinishedHandler(blobstore_handlers.BlobstoreUploadHandler):
  def post(self, client_key):
    uploads = self.get_uploads()
    if uploads is None:
      logging.error("No uploads.")
      self.error(500)
      return
    
    if len(uploads) != 1:
      logging.error("Wrong number of uploads, deleting all of them.")
      for upload in uploads:
        upload.delete()
      self.error(500)
      return

    if uploads[0] is None:
      logging.error("Upload was None")
      self.error(500)
      return
    upload = uploads[0]
    
    user = users.get_current_user()
    recording = recording.Recording.all() \
        .filter("client_key = ", client_key) \
        .get()
    if recording is None or recording.user != user:
      logging.error("Failed to match recording with id %d, or the recording "
                    "did not belong to user %s" % (recording_id, user))
      upload.delete()
      self.error(401)
      return
    
    recording.file_parts.append(upload.key())
    
    # TODO(peterdolan): Figure out a better redirect location
    self.redirect("/")


class CreateRecordingHandler(webapp.RequestHandler):
  def post(self, client_key, timestamp_ms, latE6, lonE6):
    timestamp_ms = long(timestamp_ms)
    latE6 = int(latE6)
    lonE6 = int(lonE6)
    
    location = db.GeoPt(latE6 / 1E6, lonE6 / 1E6)
    created_time = datetime.datetime.fromtimestamp((int) (timestamp_ms/1000))
    
    record = recording.Recording(client_key = client_key,
                                 location = location,
                                 created_time = created_time,
                                 random_number = common.GetRandomNumber())
    record.put()
    
    # TODO(peterdolan): Redirect to an appropriate location, for web form users
    self.redirect("/")
    

class GetMyRecordingsHandler(webapp.RequestHandler):
  def get(self):
    user = users.get_current_user()
    query = recording.Recording.all()
    query.filter("user = ", user)
    self.response.out.write("\n".join(map(lambda rec: rec.client_key,
                                          query)))


