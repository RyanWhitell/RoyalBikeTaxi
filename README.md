# Royal Bike Taxi Android

### A high-level overview of how the app and works and how it utilizes the Firebase handshaking protocol can be found [here](http://ryanwhitell.com/firebase_handshake)

Royal Bike Taxi for Android is essentially a taxi dispatch app for the bike taxi service in Savannah, GA.
It works much the same way as Uber does, with a few differences.

1. User requests a dispatch and is connected to the nearest available driver.
2. Driver either declines, in which case the user will be notified and try the next closest driver,
or the diver accepts and is connected to the user.
3. The user can see the driver as they approach, the driver can see the user's location and movement as well.
4. The driver is in charge of ending the ride.

The project highlights some Android fundamental concepts such as:
-	Google Play Services (Map, Location)
-	Background Services
-	Foreground Notifications
-	Database (Firebase)
-   Handshaking
-	Battery and Data Efficiency
-	Themes, Navigation
-	Intents

The app needs finishing touches before being used for real. Such as being able to handle lost connections, checking for compatibility (Google Play version, location enabled, network connection), and UI updates.

## Getting Started

### Step 1
Provide your own Google Maps API Key under:

*\RoyalBikeTaxi\app\src\debug\res\values\google_maps_api.xml

### Step 2
Create a new Firebase project. Create and download a configuration file to replace:

*\RoyalBikeTaxi\app\google-services.json

### Step 4
Upload the database_init.json file into the Firebase console "Database" view

### Step 5

Clean and Build Project -> Run

Click the driver icon in the navigation drawer 7 times to navigate to the login page.

Credentials to sign in as driver:


Name: Ryan
Password: m