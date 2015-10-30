//
//  DweetController.h
//  Dweet Sensor Gateway
//
//  Created by Tim Buick on 2015-07-09.
//  Copyright (c) 2015 BugLabs. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <QuartzCore/QuartzCore.h>

#import "Dweet_ios.h"

@interface DweetController : UIViewController <UITableViewDataSource,UITableViewDelegate,CBPeripheralDelegate,UIActivityItemSource> {
  
  NSMutableDictionary *characteristicToServiceLink;
  
  NSMutableDictionary *currentServices;
  NSMutableDictionary *activeCharacteristics;

  
  CBCharacteristic *temperatureChar;
  CBCharacteristic *pressureChar;
  CBCharacteristic *humidityChar;
  CBCharacteristic *accelChar;
  CBCharacteristic *magChar;

  NSMutableDictionary *dweetData;
  NSMutableDictionary *tableData;
  
  float temperature,pressure,humidity;
  float quat_x,quat_y,quat_z,quat_w;
  float mag_x,mag_y,mag_z;
  float heading;
  
  BOOL serviceDetected;
  
  NSTimer *dweetTimer;
  
  NSString *thingName;
  float freq;

}

@property (nonatomic, retain) CBPeripheral *connectedPeripheral;
@property (nonatomic, retain) id deviceConfigJson;

@property (nonatomic, retain) IBOutlet UINavigationItem *navItem;

@property (nonatomic, retain) IBOutlet UITableView *dataTV;

@property (nonatomic, retain) IBOutlet UILabel *hwLabel;
@property (nonatomic, retain) IBOutlet UILabel *thingLabel;
@property (nonatomic, retain) IBOutlet UIView *statusV;

@property (nonatomic, retain) IBOutlet UIView *wrapV;



@end
