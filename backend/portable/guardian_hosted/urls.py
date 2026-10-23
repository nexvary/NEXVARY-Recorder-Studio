from django.urls import re_path
from .views import api
urlpatterns = [re_path(r'^(?P<path>.*)$', api)]
