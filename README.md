# XPrivateSD
[Xposed module] Android per App storage/SD card sandbox

This work is based on XInternalSD by pylerSM https://github.com/pylerSM/XInternalSD and SDLink by Richard-Tung https://github.com/Richard-Tung/SDLink.

The low level implemention is hook on all the 4 constructor methods of java.io.File, update the path on SD card into a app special folder. So the impacted app is in a sandbox which can only access within it's folder. Of course, you can specify some folder as exception cases.

The App should can be build and work with Android 4.0.3(SDK15) and above, with the latest Xposed framework. However, I only test it on 7.1.1 and 8. It also works with EdXposed and VirtualXposed.
