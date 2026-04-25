import PyInstaller.__main__
import os

# Get the current directory
current_dir = os.path.dirname(os.path.abspath(__file__))

PyInstaller.__main__.run([
    'gui_mouse_server.py',  # your main script
    '--name=LazyController',  # name of your app
    '--onefile',  # create a single executable
    '--noconsole',  # don't show console window
    '--icon=icon.ico',  # your app icon
    '--add-data=icon.ico;.',  # include the icon file
    '--clean',  # clean cache
    '--windowed',  # Windows specific graphical application
    f'--workpath={os.path.join(current_dir, "build")}',  # build directory
    f'--distpath={os.path.join(current_dir, "dist")}',  # dist directory
])
