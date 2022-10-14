# Prayer Time Parser

## Purpose

The main purpose of this program is to download the prayer times from various countries online, and store them in a format that will be used within the mobile application later.

## Running the program

### Compiling the program

Just run the following command from the root folder of the repository to compile the program:

`javac -cp "lib/pdfbox-app-2.0.27.jar:." -d build ./io/gahfy/muslimcompanion/App.java`

### Running the program

Just run the following command, after compilation, in order to run the program:

`java -cp "lib/pdfbox-app-2.0.27.jar:build" io.gahfy.muslimcompanion.App`

### Cleaning

In order to clean the build files (in case you updated the sources), remove the build folder at the root of the repository.

## File format

The format of the files will mainly depends on the country.

### Algeria

The path of the files are `Algeria/[Wilaya].bin` or `Algeria/[Wilaya]/[Daira].bin` whether the times are for the wilaya or for a specific Daïra.

The content of the file is a list of 16 bytes block as follows:

* 2 Bytes for the gregorian date
  * 5 bites for the day, followed by 4 bits for the month, followed by 7 bits for the year modulo 100
* 2 Bytes for the islamic date
  * Same rule as for gregorian date
* 2 Bytes for each prayer times (6 in totals with Shorouq)
  * 1 Byte for the hour
  * 1 Byte for the minutes
