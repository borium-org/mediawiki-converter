# MediaWiki Converter

This is a converter from MediaWiki pages to a static simplified web side that is suitable for hosting on GitHub pages.

The test environment for this converter is the Borium web site that is maintained locally using Bitnami Mediawiki 1.37.1-0. Other versions may have pages in a layout that has unexpected elements, so other versions are not guaranteed to work. I don't intend to upgrade Mediawiki any time soon, so this environment will be stable enough for development.

The converter in its first version is just reading in the input page line by line and dropping lines that should not be in the output file. No magic processing of HTML DOM or anything...

# Usage

This app assumes there is a running Mediawiki instance and the host has 'wget' utility available. The one I'm using is from CygWin package.

1. Go to intermediate directory
2. Execute wget:
	wget --recursive --page-requisites --html-extension --convert-links --no-parent -R "*Special*" -R "*action=*" -R "*printable=*"  -R "*oldid=*" -R "*title=Talk:*" -R "*limit=*" http://foo.com/wiki/index.php/Main_Page
3. Execute the converter app. I do it from Eclipse with default parameters coded into a response file:
	MediaWikiConverter @ResponseFile
