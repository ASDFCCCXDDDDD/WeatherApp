/*********************************************************************
 This is an example for our nRF51822 based Bluefruit LE modules

 Pick one up today in the adafruit shop!

 Adafruit invests time and resources providing this open source code,
 please support Adafruit and open-source hardware by purchasing
 products from Adafruit!

 MIT license, check LICENSE for more information
 All text above, and the splash screen below must be included in
 any redistribution
*********************************************************************/

/*
    Please note the long strings of data sent mean the *RTS* pin is
    required with UART to slow down data sent to the Bluefruit LE!
*/

#include <Arduino.h>
#include <SPI.h>
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_BluefruitLE_UART.h"

#include "BluefruitConfig.h"

#include <Adafruit_CircuitPlayground.h>

#if SOFTWARE_SERIAL_AVAILABLE
  #include <SoftwareSerial.h>
#endif

// Create the bluefruit object, either software serial...uncomment these lines

//SoftwareSerial bluefruitSS = SoftwareSerial(BLUEFRUIT_SWUART_TXD_PIN, BLUEFRUIT_SWUART_RXD_PIN);
//Adafruit_BluefruitLE_UART ble(bluefruitSS, BLUEFRUIT_UART_MODE_PIN,BLUEFRUIT_UART_CTS_PIN, BLUEFRUIT_UART_RTS_PIN);

/* ...or hardware serial, which does not need the RTS/CTS pins. Uncomment this line */
Adafruit_BluefruitLE_UART ble(Serial1, BLUEFRUIT_UART_MODE_PIN);



// A small helper
void error(const __FlashStringHelper*err) {
  Serial.println(err);
  while (1);
}

/* The service information */

/* Thermo(a.k.a T ) Service Definitions
 * Thermo Service:  0x180D
 * Temperature Characteristic: 0x2A37
 * ButtonClick Characteristic:   0x2A38
 */
int32_t UWTCServiceUUID;
int32_t temperatureCharacteristicUUID;
int32_t ButtonClickCharacteristicUUID;

bool leftButtonPressed;
bool rightButtonPressed;

/**************************************************************************/
/*!
    @brief  Sets up the HW an the BLE module (this function is called
            automatically on startup)
*/
/**************************************************************************/
void setup(void)
{

  while (!Serial); // required for Flora & Micro
  delay(500);

  boolean success;

  Serial.begin(115200);
  Serial.println(F("Adafruit Bluefruit Thermo(a.k.a T )"));
  Serial.println(F("---------------------------------------------------"));

  randomSeed(micros());


  /* Initialise the module */
  Serial.print(F("Initialising the Bluefruit LE module: "));

  if ( !ble.begin(VERBOSE_MODE) )
  {
    error(F("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring?"));
  }
  Serial.println( F("OK!") );

  /* Perform a factory reset to make sure everything is in a known state */
  Serial.println(F("Performing a factory reset: "));
  if (! ble.factoryReset() ){
       error(F("Couldn't factory reset"));
  }

  /* Disable command echo from Bluefruit */
  ble.echo(false);

  Serial.println("Requesting Bluefruit info:");
  /* Print Bluefruit information */
  ble.info();

  // this line is particularly required for Flora, but is a good idea
  // anyways for the super long lines ahead!
  ble.setInterCharWriteDelay(5); // 5 ms

  // this line is particularly required for Flora, but is a good idea
  // anyways for the super long lines ahead!
  ble.setInterCharWriteDelay(5); // 5 ms

  /* Change the device name to make it easier to find */
  Serial.println(F("Setting device name to 'Thermo': "));

  if (! ble.sendCommandCheckOK(F("AT+GAPDEVNAME=Thermo")) ) {
    error(F("Could not set device name?"));
  }

  /* Add the Thermo(a.k.a T ) Service Definition */
  /* Service ID should be 1 */
  Serial.println(F("Adding the Thermo(a.k.a T ) Service Definition (UUID = 0x180D): "));
  success = ble.sendCommandWithIntReply( F("AT+GATTADDSERVICE=UUID=0x180D"), &UWTCServiceUUID);
  if (! success) {
    error(F("Could not add UWTC service"));
  }

  /* Add the Temperature Characteristic */
  /* Chars ID for Measurement should be 1 */
  Serial.println(F("Adding the Heart Rate Temperature Characteristic (UUID = 0x2A37): "));
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID=0x2A37, PROPERTIES=0x10, MIN_LEN=1, MAX_LEN=5, VALUE=00-40"), &temperatureCharacteristicUUID);
    if (! success) {
    error(F("Could not add UWTC Temperature Characteristic"));
  }

  /* Add the ButtonClick Characteristic */
  /* Chars ID for Body should be 2 */
  Serial.println(F("Adding the ButtonClick Characteristic (UUID = 0x2A38): "));
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID=0x2A38, PROPERTIES=0x10, MIN_LEN=1, MAX_LEN=5, VALUE=00-55"), &ButtonClickCharacteristicUUID);
    if (! success) {
    error(F("Could not add BSL characteristic"));
  }

  /* Add Thermo(a.k.a T ) Service to the advertising data (needed for Nordic apps to detect the service) */
  Serial.print(F("Thermo(a.k.a T ) Service UUID to the advertising payload: "));
  ble.sendCommandCheckOK( F("AT+GAPSETADVDATA=02-01-06-05-02-0d-18-0a-18") );

  /* Reset the device for the new service setting changes to take effect */
  Serial.print(F("Performing a SW reset (service changes require a reset): "));
  ble.reset();

  // this line is particularly required for Flora, but is a good idea
  // anyways for the super long lines ahead!
  //ble.setInterCharWriteDelay(5); // 5 ms

  CircuitPlayground.begin();

  Serial.println();
}

/** Send randomized heart rate data continuously **/
void loop(void)
{
  //int temperature = random(50, 100);
  int temperature = CircuitPlayground.temperature();

  /* Command is sent when \n (\r) or println is called */
  /* AT+GATTCHAR=CharacteristicID,value */
  ble.print( F("AT+GATTCHAR=") );
  ble.print( temperatureCharacteristicUUID );
  ble.print( F(",00-") );
  ble.println(temperature, HEX);

  /* Check if command executed OK */
  if ( !ble.waitForOK() )
  {
    Serial.println(F("Failed to get response!"));
  }

  /* Delay before next measurement update */
}
