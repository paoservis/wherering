# Explain the exit behavior #

The notable places configuration you provide instructs `WhereRing` on how to set your ringer mode when you enter a place. That leaves your ringer mode preferences undefined for most of the world! `WhereRing` follows two rules:
  1. Leave the ringer in the state it was found
  1. Humans know best
More precisely, `WhereRing` notices the old ringer mode when you move from a non-place into a place before it sets the new mode. When you leave the place, it restores the old ringer mode. Unless you change the mode (by means other than `WhereRing`) while you're in the place; in that case it allows your temporary override to stand until you enter some other place.

If you want to take over indefinitely, deactivate `WhereRing`.

# How does `WhereRing` conserve energy? #

`WhereRing` was designed to work with smallish places, like an office or a small private home. Locating that accurately normally requires GPS, which is power-hungry. `WhereRing`'s use of GPS does allow your Android to sleep for up to 4 minutes at a time.

As of 1.79, place radii are configurable. It's conceivable that `WhereRing` could resort to lower-energy (and lower-accuracy) positioning technologies if your places were sufficiently large or distant from each other. I probably won't implement that unless somebody asks for it.

# What version do I have? #

The relationship between the displayed version number (i.e., android:versionCode) (e.g., 1.49.0) and hg revnums/ids is described in detail on my [blog](http://seanfoy.blogspot.com/2009/10/version-numbers-and-dvcs.html). In short, the minor version number (49 for 1.49.0) is the mercurial local revision number as observed at the googlecode repo. If you wish to reproduce the build of version 1.49.0, clone this repository and `hg update --rev 49`.

# What alternatives exist? #

If you want (many) more features, check out [Locale](http://www.twofortyfouram.com/). If you want rings to change according to time rather than according to your position in space, try [Scharing](http://scharing.kenai.com/).