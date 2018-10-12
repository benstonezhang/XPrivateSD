# XPrivateSD
[Xposed module] Android per App storage/SD card sandbox

This work is based on XInternalSD by pylerSM https://github.com/pylerSM/XInternalSD and SDLink by Richard-Tung https://github.com/Richard-Tung/SDLink.

The low level implemention is hook on all the 4 constructor methods of java.io.File, update the path on SD card into a app special folder. So the impacted app is in a sandbox which can only access within it's folder, except for Android/data and Android/obb.
