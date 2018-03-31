Mopidy in a virtualen:

    mkvirtualenv mopidy -p `which python2.7` --system-site-packages
    pip install mopidy
    pip install --upgrade "tornado<5"
    pip install mopidy-api-explorer mopidy-iris mopidy-local-images mopidy-material-webclient
