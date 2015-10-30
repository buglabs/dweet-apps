//
//  ViewController.h
//  dweet-ios-sensor
//
//  Created by Tim Buick on 2015-06-09.
//  Copyright (c) 2015 PBJ Studios. All rights reserved.
//

#import <UIKit/UIKit.h>

#import <CoreMotion/CoreMotion.h>
#import <CoreLocation/CoreLocation.h>
#import <AVFoundation/AVFoundation.h>

#import "Dweet_ios.h"
#import "ConfigController.h"


@interface ViewController : UIViewController <UITableViewDataSource,UITableViewDelegate,CLLocationManagerDelegate> {
  
  CMMotionManager *motionManager;
  
  CLLocationManager *locationManager;
  
  NSString *thingName;
  float freq;

  NSTimer *dweetloop;
  NSTimer *sensorloop;
  
  
  NSString *dev,*devname,*iosver,*headphones;
  float brightness;
  float ax,ay,az;
  float gx,gy,gz;
  float r,p,y;
  double lat,lng;
  float horizAc,vertAc;
  float alt,head,speed;
  
  
}


@property (nonatomic, retain) IBOutlet UINavigationItem *navItem;

@property (nonatomic, retain) IBOutlet UITableView *tableV;
@property (nonatomic, retain) IBOutlet UILabel *dweetname;
@property (nonatomic, retain) IBOutlet UIView *statusled;

@property (nonatomic, retain) IBOutlet UIView *topView;

@end

