#include <SoftwareSerial.h>
#include <Bonezegei_DHT11.h>

#define TRIG 12
#define ECHO 13

Bonezegei_DHT11 dht(A0);
SoftwareSerial  BT(8, 7);

constexpr uint8_t       MAX_RECORDS = 144;
constexpr unsigned long INTERVAL    = 600000UL;

bool          sending     = false;

float         soundSpeeds[MAX_RECORDS];
float         temperatures[MAX_RECORDS];

uint8_t       recordCount = 0;
uint8_t       writeIndex  = 0;
uint8_t       sendIndex   = 0;

unsigned long lastMeasure;

// 기존 센서 코드로 구현
float measureSoundSpeed() {
  long duration;

  digitalWrite(TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG, LOW);

  duration   = pulseIn(ECHO, HIGH);
  if (duration != 0){
    return 100000000.0f /duration;
  } else {
    return 0.0f;
  }
};

float measureTemperature() {
  if (dht.getData()){
    return dht.getTemperature() - 1.7f;
  } else {
    return 0.0f;
  }
};

void setup() {
  Serial.begin(9600);
  BT.begin(9600);

  pinMode(TRIG, OUTPUT);
  pinMode(ECHO, INPUT);
  dht.begin();

  lastMeasure = millis();
}

void loop() {
  unsigned long now = millis();

    // 1. 10분마다 측정 및 저장
  if (now - lastMeasure >= INTERVAL) {

    lastMeasure += INTERVAL;

    float speed = measureSoundSpeed();
    float temp = measureTemperature();

    soundSpeeds[writeIndex]  = speed;
    temperatures[writeIndex] = temp;

    writeIndex = (writeIndex + 1) % MAX_RECORDS;

    if (recordCount < MAX_RECORDS) {
      recordCount++;
    }

    Serial.print("Measured: ");
    Serial.println(recordCount);
  }

    // 2. 블루투스 명령 수신
  while (BT.available()) {
    char cmd = BT.read();

    if (cmd == 'D' && !sending) {
      sendIndex = 0;
      sending = true;
      }
  }

    // 3. 측정과 전송을 분리해 순차 전송
  if (sending && sendIndex < recordCount) {
    BT.write(
      reinterpret_cast<const uint8_t*>( &soundSpeeds[sendIndex]),
      sizeof(float)
    );

    BT.write(
      reinterpret_cast<const uint8_t*>( &temperatures[sendIndex]),
      sizeof(float)
    );

      sendIndex++;
    }

    // 4. 전송 완료
    if (sending && sendIndex >= recordCount) {
        BT.write('E'); // 종료 마커
        sending = false;
    }
}