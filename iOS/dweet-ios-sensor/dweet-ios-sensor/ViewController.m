//
//  ViewController.m
//  dweet-ios-sensor
//
//  Created by Tim Buick on 2015-06-09.
//  Copyright (c) 2015 PBJ Studios. All rights reserved.
//

#import "ViewController.h"

@interface ViewController ()

@end

@implementation ViewController

@synthesize navItem;
@synthesize tableV;
@synthesize topView;
@synthesize dweetname;
@synthesize statusled;

- (void)viewDidLoad {
  [super viewDidLoad];
  
  NSLog(@"ViewDidLoad");
  
  motionManager = [[CMMotionManager alloc] init];
  
  locationManager=[[CLLocationManager alloc] init];
  
  locationManager.delegate=self;
  locationManager.desiredAccuracy=kCLLocationAccuracyBest;
  locationManager.distanceFilter=500;
  if ([locationManager respondsToSelector:@selector(requestWhenInUseAuthorization)]) {
    [locationManager requestWhenInUseAuthorization];
  }
  
  [Dweet_ios setDebugLevel:SHOW_ALL];
  
  // recover the saved thing name
  thingName = [[NSUserDefaults standardUserDefaults] objectForKey:@"thing"];
  
  
  /////////////////
  // remove the next line if you don't
  // want a new random thing name each time app starts
  //thingName = nil;
  /////////////////
  
  // there is no saved thing name, generate a new random name
  if (!thingName) {
    NSMutableString *name = [NSMutableString string];
    
    {
      NSString* fileRoot = [[NSBundle mainBundle]
                            pathForResource:@"adjectives" ofType:@"txt"];
      NSString* fileContents = [NSString stringWithContentsOfFile:fileRoot
                                                         encoding:NSUTF8StringEncoding error:nil];
      NSArray* allLinedStrings = [fileContents componentsSeparatedByCharactersInSet:
                                  [NSCharacterSet newlineCharacterSet]];
      
      int rand1 = arc4random()%[allLinedStrings count];
      [name appendString:[allLinedStrings objectAtIndex:rand1]];
    }
    
    [name appendString:@"-"];
    
    {
      NSString* fileRoot = [[NSBundle mainBundle]
                            pathForResource:@"nouns" ofType:@"txt"];
      NSString* fileContents = [NSString stringWithContentsOfFile:fileRoot
                                                         encoding:NSUTF8StringEncoding error:nil];
      NSArray* allLinedStrings = [fileContents componentsSeparatedByCharactersInSet:
                                  [NSCharacterSet newlineCharacterSet]];
      
      int rand1 = arc4random()%[allLinedStrings count];
      [name appendString:[allLinedStrings objectAtIndex:rand1]];
    }
    
    // save the random thing name
    [[NSUserDefaults standardUserDefaults] setObject:name forKey:@"thing"];
    [[NSUserDefaults standardUserDefaults] setObject:name forKey:@"randomized-thing"];
  }
  
  // default case for the dweet frequency
  if (![[NSUserDefaults standardUserDefaults] objectForKey:@"freq"]) {
    [[NSUserDefaults standardUserDefaults] setObject:@(1.0) forKey:@"freq"];
  }
  
  
  // create the Nav Bar
  UIView *tv = [[UIView alloc] initWithFrame:CGRectMake(0,0,80,44)];
  UIImageView *iv = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"buglogo.png"]];
  iv.frame = CGRectMake(0,12,80,18);
  [tv addSubview:iv];
  navItem.titleView = tv;
  [self.navigationController.navigationBar setBarTintColor:[UIColor darkGrayColor]];
  [self.navigationController.navigationBar setTranslucent:NO];
  [self.navigationController.navigationBar setTintColor:[UIColor whiteColor]];
  

  
}


- (void) viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  
  // adjust layout for different devices
  CGRect screenBounds = [[UIScreen mainScreen] bounds];
  tableV.frame = CGRectMake(10,60,screenBounds.size.width-20,screenBounds.size.height-130);
  topView.center = CGPointMake(screenBounds.size.width/2, topView.center.y);
  
  thingName = [[NSUserDefaults standardUserDefaults] objectForKey:@"thing"];
  freq = [[[NSUserDefaults standardUserDefaults] objectForKey:@"freq"] floatValue];
  
  NSLog(@"thing name : %@    freq : %0.2f",thingName,freq);
  dweetname.text = thingName;

  statusled.backgroundColor = [UIColor grayColor];

  motionManager.accelerometerUpdateInterval = 0.05;
  [motionManager startAccelerometerUpdates];
  motionManager.gyroUpdateInterval = 0.05;
  [motionManager startGyroUpdates];
  motionManager.deviceMotionUpdateInterval = 0.05;
  [motionManager startDeviceMotionUpdates];
  
  
  if([CLLocationManager locationServicesEnabled]){
    [locationManager startUpdatingLocation];
  }
  
  dweetloop = [NSTimer scheduledTimerWithTimeInterval:freq target:self selector:@selector(handleDweet:) userInfo:nil repeats:YES];
  sensorloop = [NSTimer scheduledTimerWithTimeInterval:0.25 target:self selector:@selector(updateSensors:) userInfo:nil repeats:YES];
  

}

- (void) viewWillDisappear:(BOOL)animated {
  [super viewWillDisappear:animated];
  
  [motionManager stopAccelerometerUpdates];
  [motionManager stopDeviceMotionUpdates];
  [motionManager startGyroUpdates];
  
  if([CLLocationManager locationServicesEnabled]){
    [locationManager stopUpdatingLocation];
  }

  [dweetloop invalidate];
  dweetloop = nil;
  [sensorloop invalidate];
  sensorloop = nil;

}

- (void)didReceiveMemoryWarning {
  [super didReceiveMemoryWarning];
}





- (IBAction) configHit {
  
  // bring up dweet config view
  ConfigController *cc = (ConfigController*)[self.storyboard instantiateViewControllerWithIdentifier:@"ConfigController"];
  [self.navigationController pushViewController:cc animated:YES];
  
}





- (IBAction) shareHit {

  // bring up an activityViewController to share dweet link with someone
  
  NSString *textToShare = [NSString stringWithFormat:@"Follow my iPhone sensor data : https://dweet.io/follow/%@",thingName];
  
  NSArray *objectsToShare = @[self,textToShare];
  
  UIActivityViewController *activityVC = [[UIActivityViewController alloc] initWithActivityItems:objectsToShare applicationActivities:nil];
  
  NSArray *excludeActivities = @[UIActivityTypeAirDrop,
                                 UIActivityTypePrint,
                                 UIActivityTypeAssignToContact,
                                 UIActivityTypeSaveToCameraRoll,
                                 UIActivityTypeAddToReadingList,
                                 UIActivityTypePostToFlickr,
                                 UIActivityTypePostToVimeo];
  
  activityVC.excludedActivityTypes = excludeActivities;
  
  [self presentViewController:activityVC animated:YES completion:nil];
  
  
}


// ActivityViewController delegate methods

- (id)activityViewController:(UIActivityViewController *)activityViewController itemForActivityType:(NSString *)activityType
{
  NSLog(@"in itemForAct");
  return @"";
}
- (id)activityViewControllerPlaceholderItem:(UIActivityViewController *)activityViewController
{
  NSLog(@"in actVCPlaceholder");
  return @"";
}
- (NSString *)activityViewController:(UIActivityViewController *)activityViewController subjectForActivityType:(NSString *)activityType
{
  NSLog(@"in subjForAct");
  return @"iPhone Sensor Data";
}








- (void) updateSensors:(NSTimer*)theTimer {
  
  dev = [UIDevice currentDevice].model;
  devname = [UIDevice currentDevice].name;
  iosver = [NSString stringWithFormat:@"%@ %@",[UIDevice currentDevice].systemName,[UIDevice currentDevice].systemVersion];
  headphones = [self isHeadsetPluggedIn] ? @"Yes" : @"No";
  
  float b = [UIScreen mainScreen].brightness;
  brightness = b*100;
  
  p = 180/M_PI*motionManager.deviceMotion.attitude.pitch;
  r = 180/M_PI*motionManager.deviceMotion.attitude.roll;
  y = 180/M_PI*motionManager.deviceMotion.attitude.yaw;
  
  ax = motionManager.accelerometerData.acceleration.x;
  ay = motionManager.accelerometerData.acceleration.y;
  az = motionManager.accelerometerData.acceleration.z;
  
  gx = motionManager.gyroData.rotationRate.x;
  gy = motionManager.gyroData.rotationRate.y;
  gz = motionManager.gyroData.rotationRate.z;
    
  [tableV reloadData];
  
}





- (void) handleDweet:(NSTimer *)theTimer {
  
  NSDictionary *dict = [NSDictionary dictionaryWithObjectsAndKeys:
                        [NSString stringWithFormat:@"%0.2f",brightness],@"Brightness",
                        [NSString stringWithFormat:@"%0.2f",p],@"Pitch",
                        [NSString stringWithFormat:@"%0.2f",r],@"Roll",
                        [NSString stringWithFormat:@"%0.2f",y],@"Yaw",
                        [NSString stringWithFormat:@"%0.2f",ax],@"AccX",
                        [NSString stringWithFormat:@"%0.2f",ay],@"AccY",
                        [NSString stringWithFormat:@"%0.2f",az],@"AccZ",
                        [NSString stringWithFormat:@"%0.2f",gx],@"RotX",
                        [NSString stringWithFormat:@"%0.2f",gy],@"RotY",
                        [NSString stringWithFormat:@"%0.2f",gz],@"RotZ",
                        [NSString stringWithFormat:@"%f",lat],@"Latitude",
                        [NSString stringWithFormat:@"%f",lng],@"Longitude",
                        [NSString stringWithFormat:@"%0.2f",alt],@"Altitude",
                        [NSString stringWithFormat:@"%0.2f",head],@"Heading",
                        [NSString stringWithFormat:@"%0.2f",speed],@"Speed",
                        [NSString stringWithFormat:@"%0.2f",horizAc],@"HorizAcc",
                        [NSString stringWithFormat:@"%0.2f",vertAc],@"VertAcc",
                        [NSString stringWithFormat:@"%@",dev],@"Model",
                        [NSString stringWithFormat:@"%@",devname],@"Name",
                        [NSString stringWithFormat:@"%@",iosver],@"OSVer",
                        [NSString stringWithFormat:@"%@",headphones],@"HeadphoneConn",
                        nil];
  
  [Dweet_ios sendDweet:dict toThing:thingName lockedWithKey:nil withCallback:@selector(dweet_callback:) onTarget:self overwriteData:NO];

}


- (void) dweet_callback:(NSArray*)rsp {
 
  NSInteger rslt_code = [[rsp objectAtIndex:0] integerValue];
  NSString *rslt_string = [rsp objectAtIndex:1];
  
  if (rslt_code<0) {
    // the result was an error
    statusled.backgroundColor = [UIColor redColor];
  } else {
    // the result was ok
    statusled.backgroundColor = [UIColor greenColor];
  }
  // flash the status indicator
  [UIView animateWithDuration:0.2 animations:^{
    statusled.alpha = 0.3;
  } completion:^(BOOL finished) {
    statusled.alpha = 1.0;
  }];


  if (rslt_code==0) {
    NSLog(@"in callback success : %@",rslt_string);
    NSLog(@"\n");
  } else {
    NSLog(@"in callback error:%ld",(long)rslt_code);
  }
  
}






// Table View Delegate methods
/////////////////////////////////


- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
  return 3;
}

- (NSString*)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section {
  if (section==0) {
    return @"DEVICE";
  } else if (section==1) {
    return @"ORIENTATION";
  } else if (section==2) {
    return @"GPS";
  }
 
  return nil;
}

- (void)tableView:(UITableView *)tableView willDisplayHeaderView:(UIView *)view forSection:(NSInteger)section
{
  UITableViewHeaderFooterView *v = (UITableViewHeaderFooterView *)view;
  v.backgroundView.backgroundColor = [UIColor lightGrayColor];
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
  if (section==0) {
    return 5;
  } else if (section==1) {
    return 9;
  } else if (section==2) {
    return 7;
  }
  return 0;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
  return 40;
}

- (UITableViewCell *)tableView:(UITableView *)mtableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
  
  UITableViewCell *cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleValue1 reuseIdentifier:nil];
  
  cell.textLabel.font = [UIFont systemFontOfSize:15];
  cell.detailTextLabel.font = [UIFont systemFontOfSize:13];
  
  if ([indexPath section]==0) {
    switch ([indexPath row]) {
      case 0:
        cell.textLabel.text = @"Model";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%@",dev];
        break;
      case 1:
        cell.textLabel.text = @"Name";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%@",devname];
        break;
      case 2:
        cell.textLabel.text = @"iOS Version";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%@",iosver];
        break;
      case 3:
        cell.textLabel.text = @"Brightness";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f %%",brightness];
        break;
      case 4:
        cell.textLabel.text = @"Headphones Attached";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%@",headphones];
        break;
    }
  }
  
  if ([indexPath section]==1) {
    switch ([indexPath row]) {
      case 0:
        cell.textLabel.text = @"Accelerometer : X";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f G",ax];
        break;
      case 1:
        cell.textLabel.text = @"Accelerometer : Y";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f G",ay];
        break;
      case 2:
        cell.textLabel.text = @"Accelerometer : Z";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f G",az];
        break;
      case 3:
        cell.textLabel.text = @"Rotation Rate :  X";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f rad/s",gx];
        break;
      case 4:
        cell.textLabel.text = @"Rotation Rate : Y";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f rad/s",gy];
        break;
      case 5:
        cell.textLabel.text = @"Rotation Rate : Z";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f rad/s",gz];
        break;
      case 6:
        cell.textLabel.text = @"Attitude : Roll";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f°",r];
        break;
      case 7:
        cell.textLabel.text = @"Attitude : Pitch";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f°",p];
        break;
      case 8:
        cell.textLabel.text = @"Attitude : Yaw";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f°",y];
        break;
    }
  }
  
  if ([indexPath section]==2) {
    switch ([indexPath row]) {
      case 0:
        cell.textLabel.text = @"Latitude";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f°",lat];
        break;
      case 1:
        cell.textLabel.text = @"Longitude";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f°",lng];
        break;
      case 2:
        cell.textLabel.text = @"Altitude";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f m",alt];
        break;
      case 3:
        cell.textLabel.text = @"Heading";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f°",head];
        break;
      case 4:
        cell.textLabel.text = @"Speed";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f m/s\u00B2",speed];
        break;
      case 5:
        cell.textLabel.text = @"Horizontal Accuracy";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f m",horizAc];
        break;
      case 6:
        cell.textLabel.text = @"Vertical Accuracy";
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%0.2f m",vertAc];
        break;
    }
  }
  
  return cell;
  
}


- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
  
  [tableView deselectRowAtIndexPath:indexPath animated:YES];
    
}




// Location Manager Delegate Methods
- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations
{
  NSLog(@"location: %@", [locations lastObject]);
  CLLocation *curr = [locations lastObject];
  lat = curr.coordinate.latitude;
  lng = curr.coordinate.longitude;
  horizAc = curr.horizontalAccuracy;
  vertAc = curr.verticalAccuracy;
  alt = curr.altitude;
  head = curr.course;
  speed = curr.speed;
}



- (BOOL)isHeadsetPluggedIn {
  AVAudioSessionRouteDescription* route = [[AVAudioSession sharedInstance] currentRoute];
  for (AVAudioSessionPortDescription* desc in [route outputs]) {
    if ([[desc portType] isEqualToString:AVAudioSessionPortHeadphones])
      return YES;
  }
  return NO;
}


@end
