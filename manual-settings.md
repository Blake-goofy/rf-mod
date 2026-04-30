# RF Device Settings

## 1. Switch to Admin Mode

1. Hold the back arrow.
2. Select **Admin**.
3. Enter the password: **0000**.

## 2. Apply the Required Settings

### Display

1. Open **Settings > Display**.
2. Set brightness to **100%**.
3. Turn off **Adaptive brightness**.

### Keyboard Toggle Service

1. Open **Settings > Accessibility > Keyboard Toggle Service**.
2. Turn on **Use Keyboard Toggle Service**.
3. Tap **Allow**.

### Map the Keyboard Key

1. Open **Settings > Key Programmer**.
2. Select **GRIP_TRIGGER_2**.
3. Set it to **Keyboard Toggle**.

> **Keyboard Toggle** is under **Shortcuts**.

### Map the NAV_PAD Key

1. Open **Settings > Key Programmer**.
2. Select **NAV_PAD**.
3. Set it to **ENTER**.

> **ENTER** is under **Keys and buttons**.

### Enterprise Keyboard Preferences

1. Open **Settings > System > Languages and input > On-screen keyboard (Virtual keyboard)**.
    - If any keyboard other than **Enterprise Keyboard** is enabled, tap **+ Manage keyboards**, turn off every keyboard except **Enterprise Keyboard**, then return to the **Virtual keyboard** screen.
2. Tap **Enterprise Keyboard > Preferences**.
3. Set **Navigation** to **Keys**.
4. Tap **Tab configuration**.
5. Set **Prefer tab** to **Numeric**.
6. Go back **Enterprise Keyboard > Text correction**.
7. Disable **Show correction suggestions**.

### DataWedge

1. Open **DataWedge > Warehouse Mobile1 > Keystroke output Basic Data Formatting**.
2. Enable **Send TAB key**.
3. Open **DataWedge > Warehouse Mobile2 > Intent output Advanced Data Formatting**.
4. Enable advanced data formatting.
5. Tap **Rules > Rule0 > Criteria > String to check for**.
6. Type 00, press ok.
7. Tap **String length**.
8. Type 18, press ok.
9. Go back, Tap **Actions**.
10. Tap the 3 dots in top right corner > **New Action > Send string**.
11. Move "Send string to the top.
12. Tap **Send string > String**.
13. Type 00, press ok.
14. Go back (3 times) until you see **Rules** at the top of the screen.
15. Tap the 3 dots in top right corner > **New rule**.
16. Type Rule1, press ok.
17. Go back (2 times) until you see **Profile: Warehouse Mobile2** at the top of the screen.
18. Open **DataWedge > Warehouse Mobile2 > Intent output Basic Data Formatting**.
19. Disable data formatting.
20. Open **DataWedge > Warehouse Mobile2 > Basic Data Formatting > Configure Scanner Settings > Decoders**.
21. Enable **Interleaved 2of5**.

> If you do not see the **Warehouse Mobile** profiles, launch **Warehouse Mobile**, log in, scan something, and then return to DataWedge. You do **not** need to leave admin mode to launch **Warehouse Mobile**

### Volume

1. Use the physical volume buttons on the top of the device.
2. Tap the volume control that appears on the edge of the screen.
3. Turn the scanner volume up.

> Some devices require turning up the ringer slider to be able to adjust the scanner volume.


## 3. Return to User Mode

1. Open **SOTI MobiControl**.
2. Hold the back arrow.
3. Select **User mode**.

## 4. Confirm the Settings

1. Launch **Warehouse Mobile**.
2. Click into the username field.
3. Press the back trigger to toggle the keyboard.
4. Confirm the keyboard does not have tabs at the top.
5. Confirm the keyboard starts on the numbers.
6. Scan badge barcode.
7. Confirm the scan tabs into the next field and makes a sound.
8. Confirm the brightness did not go down.
10. Log in to Warehouse Mobile
11. Use the NAV_PAD button to press **Enter**
11. Tap **Decant**
12. Scan a GTIN
13. Scan a Wal-Mart label and ensure it adds the 2 zeros