//
//  ViewController.h
//  Dweet Sensor Gateway
//
//  Created by Tim Buick on 2015-07-09.
//  Copyright (c) 2015 BugLabs. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <CoreBluetooth/CoreBluetooth.h>

#import "DweetController.h"
#import "ConfigController.h"

#import "Dweet_ios.h"

@interface ScanController : UIViewController <UITableViewDataSource,UITableViewDelegate, CBCentralManagerDelegate, CBPeripheralDelegate> {
  
  CBCentralManager *BtManager;
  
  NSMutableArray *knownDeviceList;
  NSMutableArray *unknownDeviceList;
  
  NSMutableDictionary *deviceConfig;
  
  int firstAutoScan;
  int poweredOn;
  
  NSString *savedThing;
  
}



@property (nonatomic, retain) IBOutlet UIView *scanV;
@property (nonatomic, retain) IBOutlet UITableView *deviceTableV;
@property (nonatomic, retain) IBOutlet UINavigationItem *navItem;

@property (nonatomic, retain) IBOutlet UIView *shadedV;


@end

