package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {


    private SecurityService serviceUnderTest;

    private Sensor testSensor;

    private final String sensorId = UUID.randomUUID().toString();

    /*
     * Mock dependencies
     */
    @Mock
    private StatusListener mockStatusListener;

    @Mock
    private ImageService mockImageService;

    @Mock
    private SecurityRepository mockSecurityRepository;


    /*
     * Runs before each test
     */
    @BeforeEach
    void setUp() {
        serviceUnderTest = new SecurityService(mockSecurityRepository, mockImageService);
        testSensor = createSensor();
    }

    private Sensor createSensor() {
        return new Sensor(sensorId, SensorType.DOOR);
    }

    private Set<Sensor> generateSensors(int numberOfSensors, boolean activeState) {

        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < numberOfSensors; i++) {
            sensors.add(new Sensor(sensorId, SensorType.DOOR));
        }
        sensors.forEach(sensor -> sensor.setActive(activeState));
        return sensors;
    }

    /*
     * TEST 1
     * When system is armed and a sensor activates, alarm should move to PENDING state.
     */
    @Test
    void ifSystemArmedAndSensorActivated_changeStatusToPending() {
        when(mockSecurityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        serviceUnderTest.changeSensorActivationStatus(testSensor, true);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    /*
     * TEST 2
     * When system already in pending state and another sensor activates, alarm should escalate to ALARM.
     */
    @Test
    void ifSystemArmedAndSensorActivatedAndPendingState_changeStatusToAlarm() {
        when(mockSecurityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        serviceUnderTest.changeSensorActivationStatus(testSensor, true);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    /*
     * TEST 3
     * If alarm is pending and sensor becomes inactive, alarm should return to NO_ALARM.
     */
    @Test
    void ifPendingAlarmAndSensorInactive_returnNoAlarmState() {
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        testSensor.setActive(false);
        serviceUnderTest.changeSensorActivationStatus(testSensor);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /*
     * TEST 4
     * Once alarm is triggered, sensor changes should not modify alarm state.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ifAlarmIsActive_changeSensorShouldNotAffectAlarmState(boolean activeStatus) {
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        serviceUnderTest.changeSensorActivationStatus(testSensor, activeStatus);
        verify(mockSecurityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /*
     * TEST 5
     * If sensor activates again while alarm is pending, alarm should escalate to ALARM.
     */
    @Test
    void ifSensorActivatedWhileActiveAndPendingAlarm_changeStatusToAlarm() {
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        testSensor.setActive(true);
        serviceUnderTest.changeSensorActivationStatus(testSensor, true);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    /*
     * TEST 6
     * If sensor is already inactive and gets deactivated again, alarm state should not change.
     */
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class)
    void ifSensorDeactivatedWhileInactive_noChangesToAlarmState(AlarmStatus alarmState) {
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(alarmState);
        testSensor.setActive(false);
        serviceUnderTest.changeSensorActivationStatus(testSensor, false);
        verify(mockSecurityRepository, never()).setAlarmStatus(any());
    }

    /*
     * TEST 7
     * If a cat is detected while system armed at home, alarm should immediately trigger.
     */
    @Test
    void ifImageServiceIdentifiesCatWhileAlarmArmedHome_changeStatusToAlarm() {
        BufferedImage catImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(mockSecurityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(mockImageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        serviceUnderTest.processImage(catImage);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    /*
     * TEST 8
     * If no cat detected and all sensors inactive, alarm should reset to NO_ALARM.
     */
    @Test
    void ifImageServiceIdentifiesNoCatImage_changeStatusToNoAlarmAsLongSensorsNotActive() {
        Set<Sensor> sensors = generateSensors(3, false);
        when(mockSecurityRepository.getSensors()).thenReturn(sensors);
        when(mockImageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(false);
        serviceUnderTest.processImage(mock(BufferedImage.class));
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /*
     * TEST 9
     * When system is disarmed, alarm must always be reset.
     */
    @Test
    void ifSystemDisarmed_setNoAlarmState() {
        serviceUnderTest.setArmingStatus(ArmingStatus.DISARMED);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }


    /*
     * TEST 10
     * When system arms, all sensors should reset to inactive.
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void ifSystemArmed_resetSensorsToInactive(ArmingStatus status) {
        Set<Sensor> sensors = generateSensors(3, true);
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(mockSecurityRepository.getSensors()).thenReturn(sensors);
        serviceUnderTest.setArmingStatus(status);
        serviceUnderTest.getSensors().forEach(sensor ->
                assertFalse(sensor.getActive())
        );
    }


    /*
     * TEST 11
     * If a cat was detected before arming home, alarm should trigger once system is armed.
     */
    @Test
    void ifSystemArmedHomeWhileImageServiceIdentifiesCat_changeStatusToAlarm() {
        BufferedImage catImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(mockImageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(mockSecurityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        serviceUnderTest.processImage(catImage);
        serviceUnderTest.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    /*
     * Additional tests for extra coverage
     */
    @Test
    void addAndRemoveStatusListener() {
        serviceUnderTest.addStatusListener(mockStatusListener);
        serviceUnderTest.removeStatusListener(mockStatusListener);
    }

    @Test
    void addAndRemoveSensor() {
        serviceUnderTest.addSensor(testSensor);
        serviceUnderTest.removeSensor(testSensor);
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM"})
    void ifSystemDisarmedAndSensorActivated_noChangesToArmingState(AlarmStatus alarmStatus) {
        when(mockSecurityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        serviceUnderTest.changeSensorActivationStatus(testSensor, true);
        verify(mockSecurityRepository, never()).setArmingStatus(ArmingStatus.DISARMED);
    }

    @Test
    void ifAlarmStateAndSystemDisarmed_changeStatusToPending() {
        when(mockSecurityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        serviceUnderTest.changeSensorActivationStatus(testSensor);
        verify(mockSecurityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }
}
