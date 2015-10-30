//
//  DweetController.m
//  Dweet Sensor Gateway
//
//  Created by Tim Buick on 2015-07-09.
//  Copyright (c) 2015 BugLabs. All rights reserved.
//

#import "DweetController.h"

#import "DDMathParser.h"

/////////////////////
// Hardcoded profiles for ST BlueMS1

#define ACCELERATION_SERVICE  @"1BC5D5A5-0200-B49A-E111-3ACF806E3602"
#define CALIBRATION_CHAR      @"1BC5D5A5-0200-FC8F-E111-4ACFA0783EE2"
#define SENSORFUSION_CHAR     @"1BC5D5A5-0200-36AC-E111-4BCF801B0A34"

#define MAG_SERVICE           @"1BC5D5A5-0200-B49A-E111-3ACF806E3620"
#define MAG_CHAR              @"1BC5D5A5-0200-73A0-E211-8CE4600BC502"

// If you have BlueMS firmware with a license for MotionFX sensor fusion
// you can USE_SENSOR_FUSION=1 to have the board return quaternions.
// With USE_SENSOR_FUSION=0 only the magnetometer will be used
// and a compass heading will be generated with the data.
#define USE_SENSOR_FUSION 0


#define ENVIRONMENTAL_SERVICE @"1BC5D5A5-0200-D082-E211-77E4401A8242"
#define TEMPERATURE_CHAR      @"1BC5D5A5-0200-E3A9-E211-77E420552EA3"
#define PRESSURE_CHAR         @"1BC5D5A5-0200-0B84-E211-8BE480C420CD"
#define HUMIDITY_CHAR         @"1BC5D5A5-0200-73A0-E211-8CE4600BC501"

////////////////////////////
////////////////////////////


@interface DweetController ()

@end

@implementation DweetController

@synthesize connectedPeripheral;
@synthesize deviceConfigJson;
@synthesize navItem;
@synthesize dataTV;
@synthesize hwLabel,thingLabel,statusV;
@synthesize wrapV;

- (void)viewDidLoad {
  [super viewDidLoad];
  
  NSLog(@"in viewDidLoad");
  
  // create the Nav Bar
  UIView *tv = [[UIView alloc] initWithFrame:CGRectMake(0,0,80,44)];
  UIImageView *iv = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"buglogo.png"]];
  iv.frame = CGRectMake(0,12,80,18);
  [tv addSubview:iv];
  navItem.titleView = tv;
  navItem.rightBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemAction target:self action:@selector(hitForward)];
  
  // load saved thing name and dweet frequency
  thingName = [[NSUserDefaults standardUserDefaults] objectForKey:@"thing"];
  freq = [[[NSUserDefaults standardUserDefaults] objectForKey:@"freq"] floatValue];
  
  // set some UI components
  hwLabel.text = connectedPeripheral.name;
  thingLabel.text = thingName;
  statusV.backgroundColor = [UIColor grayColor];
  
  characteristicToServiceLink = [[NSMutableDictionary alloc] init];
  currentServices = [[NSMutableDictionary alloc] init];
  activeCharacteristics = [[NSMutableDictionary alloc] init];
  dweetData = [[NSMutableDictionary alloc] init];
  tableData = [[NSMutableDictionary alloc] init];

 }

- (void) viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  
  NSLog(@"in viewWillAppear");

  // adjust UI components based on screen size
  CGRect screenBounds = [[UIScreen mainScreen] bounds];
  wrapV.frame = CGRectMake((screenBounds.size.width-280)/2,18,320,140);
  float height = dataTV.frame.size.height;
  if (dataTV.frame.origin.y+height > screenBounds.size.height) {
    height = screenBounds.size.height - dataTV.frame.origin.y - 60;
  }
  dataTV.frame = CGRectMake((screenBounds.size.width-300)/2,dataTV.frame.origin.y,dataTV.frame.size.width,height);
  
}

- (void)viewDidAppear:(BOOL)animated {
  [super viewDidAppear:animated];

  NSLog(@"in viewDidAppear");

  // initialize variables
  temperatureChar = pressureChar = humidityChar = nil;
  connectedPeripheral.delegate = self;
  serviceDetected = NO;
  
  // start a service scan
  [self scanForServices];

}

-(void)viewDidDisappear:(BOOL)animated {
  [super viewDidDisappear:animated];
  
  NSLog(@"in viewDidDisappear");

  // make sure to disconnect delegate and timer
  connectedPeripheral.delegate = nil;
  [dweetTimer invalidate];
  dweetTimer = nil;

}

- (void)didReceiveMemoryWarning {
  [super didReceiveMemoryWarning];

}



/////// CBPeripheralDelegate methods //////
///////////////////////////////////////////


- (void)peripheral:(CBPeripheral *)peripheral didDiscoverServices:(NSError *)error {
  NSLog(@"in didDiscoverServices");
  
  if (peripheral!=connectedPeripheral) {
    NSLog(@"peripheral error!");
    return;
  }
  // Walk through discovered services looking for specific ones.
  // Start a characteristic discovery for the matching services.
  NSDictionary *devServices = [deviceConfigJson objectForKey:@"services"];
  for (CBService *service in [peripheral services]) {
    NSLog(@" - Found service %@",[self representativeString:[service UUID]]);
    
    for (NSString *serv in [devServices allKeys]) {
      if ([[self representativeString:[service UUID]] isEqualToString:serv] ) {
        if ([[[devServices objectForKey:serv] objectForKey:@"active"] boolValue]) {
          [peripheral discoverCharacteristics:nil forService:service];
          [self detectedValidService];
          [currentServices setObject:[[devServices objectForKey:serv] objectForKey:@"characteristics"] forKey:serv];
          NSLog(@"activating service : %@",[[devServices objectForKey:serv] objectForKey:@"name"]);
          
          //link all characterisitcs for this service to the service name... used for constructing the
          //tableview later
          for (NSString *cha in [[[devServices objectForKey:serv] objectForKey:@"characteristics"] allKeys]) {
            [characteristicToServiceLink setObject:[[devServices objectForKey:serv] objectForKey:@"name"] forKey:cha];
          }
          
        } else {
          NSLog(@"not activating service : %@",[[devServices objectForKey:serv] objectForKey:@"name"]);
        }
      }
    }

  }
  
}

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverCharacteristicsForService:(CBService *)service error:(NSError *)error {
  NSLog(@"in didDiscoverCharacteristicsForService : %@",[self representativeString:[service UUID]]);
  
  // For each service, walk through and look for specific characteristics.
  // Connect to each one and 'turn it on' with a notify:yes.
  // Also, capture the characteristic pointers for use when updates occur.
  
  NSDictionary *serv = [currentServices objectForKey:[self representativeString:[service UUID]]];

  /*
  // this run through handles any ENABLES!
  const unsigned char bytes[] = {1};
  NSData *data = [NSData dataWithBytes:bytes length:sizeof(bytes)];
  for (CBCharacteristic *characteristic in [service characteristics]) {
    NSLog(@" - Found characteristic %@",[self representativeString:[characteristic UUID]]);
    for (NSString *cha in [serv allKeys]) {
      if ([[self representativeString:[characteristic UUID]] isEqualToString:cha]) {
        if ([[[serv objectForKey:cha] objectForKey:@"activeE"] boolValue]) {
          
          [activeCharacteristics setObject:[serv objectForKey:cha] forKey:cha];
          [peripheral writeValue:data forCharacteristic:characteristic type:CBCharacteristicWriteWithResponse];
        
          NSLog(@"enabling characteristic : %@",[[serv objectForKey:cha] objectForKey:@"name"]);
          
        } else {
          NSLog(@"NOT enabling characteristic : %@",[[serv objectForKey:cha] objectForKey:@"name"]);
        }
      }
    }
  }
  
  [NSThread sleepForTimeInterval:1];
*/
   
  // this run through handles notifications
  
  for (CBCharacteristic *characteristic in [service characteristics]) {
    NSLog(@" - Found characteristic %@",[self representativeString:[characteristic UUID]]);
    for (NSString *cha in [serv allKeys]) {
      if ([[self representativeString:[characteristic UUID]] isEqualToString:cha]) {
        if ([[[serv objectForKey:cha] objectForKey:@"active"] boolValue]) {
          
          [activeCharacteristics setObject:[serv objectForKey:cha] forKey:cha];
          [peripheral setNotifyValue:YES forCharacteristic:characteristic];
          NSLog(@"setting notify for characteristic : %@",[[serv objectForKey:cha] objectForKey:@"name"]);
          
        } else {
          NSLog(@"NOT enabling characteristic : %@",[[serv objectForKey:cha] objectForKey:@"name"]);
        }
      }
    }
  }

  
}

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverDescriptorsForCharacteristic:(CBCharacteristic *)characteristic error:(NSError *)error {
  NSLog(@"in didDiscoverDescriptorsForCharacteristic : %@",[characteristic UUID]);
  
}

- (void)peripheral:(CBPeripheral *)peripheral didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic error:(NSError *)error {
  NSLog(@"in didUpdateValueForCharacteristic");
  
  // A characteristic just updated. Walk through and find out which one it was.
  // Update the method variables for that characteristic.
  // The characteristic data format varies, and is hardcoded for the BlueMS1 board.
  
  NSDictionary *cha = [activeCharacteristics objectForKey:[self representativeString:[characteristic UUID]]];
  
  if (!cha) {
    NSLog(@"Error! No linked characteristic");
    return;
  }
  
  if (cha) {
    NSLog(@"update from %@",[cha objectForKey:@"name"]);
  }
  
  NSDictionary *treat = [cha objectForKey:@"treatment"];
  
  unsigned char *data = (unsigned char *)[[characteristic value] bytes];

  float value[3];
  
  int width = [[treat objectForKey:@"width"] integerValue];
  float scale = [[treat objectForKey:@"scaling"] floatValue];
  BOOL sign = [[treat objectForKey:@"signed"] boolValue];
  int count = [[treat objectForKey:@"count"] integerValue];
  NSString *pp1 = [treat objectForKey:@"postprocess1"];
  NSString *pp2 = [treat objectForKey:@"postprocess2"];
  NSString *dn = [treat objectForKey:@"dweetname"];
  NSString *dv = [treat objectForKey:@"dweetval"];

  if (count==1) {
    if (width==16) {
      value[0] = ((data[1]<<8) + data[0]);
    } else if (width==24) {
      value[0] = ((data[2]<<16) + (data[1]<<8) + data[0]);
    }
    if (sign && width==16) {
      if (value[0]>=32768) value[0]-=65536;
    }
    value[0] = value[0]/scale;
  } else if (count==3) {
    if (width==16) {
      value[0] = ((data[1]<<8) + data[0]);
      value[1] = ((data[3]<<8) + data[2]);
      value[2] = ((data[5]<<8) + data[4]);
    }
    if (sign && width==16) {
      if (value[0]>=32768) value[0]-=65536;
      if (value[1]>=32768) value[1]-=65536;
      if (value[2]>=32768) value[2]-=65536;
    }
    value[0] = value[0]/scale;
    value[1] = value[1]/scale;
    value[2] = value[2]/scale;
  }
  
  

  if (count==1) {
//    NSLog(@"new value : %0.2f",value[0]);
  } else if (count==3) {
//    NSLog(@"new value : %0.2f,%0.2f,%0.2f",value[0],value[1],value[2]);
  }
  
  
  NSNumber *p1,*p2;
  if ([pp1 length]>0) {
    pp1 = [pp1 stringByReplacingOccurrencesOfString:@"%v0%" withString:[NSString stringWithFormat:@"%0.2f",value[0]]];
    pp1 = [pp1 stringByReplacingOccurrencesOfString:@"%v1%" withString:[NSString stringWithFormat:@"%0.2f",value[1]]];
    pp1 = [pp1 stringByReplacingOccurrencesOfString:@"%v2%" withString:[NSString stringWithFormat:@"%0.2f",value[2]]];
    
//    NSLog(@"pp1 string : %@",pp1);
    
    p1 = [pp1 numberByEvaluatingString];
    
    if ([pp2 length]>0) {
      pp2 = [pp2 stringByReplacingOccurrencesOfString:@"%v0%" withString:[NSString stringWithFormat:@"%0.2f",value[0]]];
      pp2 = [pp2 stringByReplacingOccurrencesOfString:@"%v1%" withString:[NSString stringWithFormat:@"%0.2f",value[1]]];
      pp2 = [pp2 stringByReplacingOccurrencesOfString:@"%v2%" withString:[NSString stringWithFormat:@"%0.2f",value[2]]];
      pp2 = [pp2 stringByReplacingOccurrencesOfString:@"%p1%" withString:[NSString stringWithFormat:@"%0.2f",[p1 floatValue]]];
    }
    
    p2 = [pp2 numberByEvaluatingString];
    
//    NSLog(@"pp2 string : %@",pp2);

  }
  
  dv = [dv stringByReplacingOccurrencesOfString:@"%v0%" withString:[NSString stringWithFormat:@"%0.2f",value[0]]];
  dv = [dv stringByReplacingOccurrencesOfString:@"%v1%" withString:[NSString stringWithFormat:@"%0.2f",value[1]]];
  dv = [dv stringByReplacingOccurrencesOfString:@"%v2%" withString:[NSString stringWithFormat:@"%0.2f",value[2]]];
  dv = [dv stringByReplacingOccurrencesOfString:@"%p1%" withString:[NSString stringWithFormat:@"%0.2f",[p1 floatValue]]];
  dv = [dv stringByReplacingOccurrencesOfString:@"%p2%" withString:[NSString stringWithFormat:@"%0.2f",[p2 floatValue]]];
 
//  NSLog(@"dv string : %@",dv);
  
//  NSLog(@"dweet : %@ -> %@",dn,dv);
  [dweetData setObject:dv forKey:dn];
  
  NSString *groupname = [characteristicToServiceLink objectForKey:[self representativeString:[characteristic UUID]]];
  NSMutableDictionary *tableGroup = [tableData objectForKey:groupname];
  if (!tableGroup) tableGroup = [[NSMutableDictionary alloc] init];
  [tableGroup setObject:dv forKey:dn];
  [tableData setObject:tableGroup forKey:groupname];
  
  // update the table view
  [dataTV reloadData];

}

- (void)peripheral:(CBPeripheral *)peripheral didUpdateValueForDescriptor:(CBDescriptor *)descriptor error:(NSError *)error {
  NSLog(@"in didUpdateValueForDescriptor");
}

- (void)peripheral:(CBPeripheral *)peripheral didUpdateNotificationStateForCharacteristic:(CBCharacteristic *)characteristic error:(NSError *)error {
  NSLog(@"in didUpdateNotificationStateForCharacteristic : %@",[self representativeString:[characteristic UUID]]);
}


- (void)peripheralDidUpdateRSSI:(CBPeripheral *)peripheral error:(NSError *)error {
  NSLog(@"in peripheralDidUpdateRSSI");
}

- (void)peripheralDidInvalidateServices:(CBPeripheral *)peripheral {
  NSLog(@"in peripheralDidInvalidateServices");
}






- (void) scanForServices {
  
  // null out the saved characteristics, and do the scan
  temperatureChar = nil;
  pressureChar = nil;
  humidityChar = nil;
  accelChar = nil;
  magChar = nil;
  [connectedPeripheral discoverServices:nil];  
  
}




- (void) detectedValidService {
  
  // if this is the first valid service detected,
  // start a timer to do the dweet updates
  
  if (serviceDetected==YES) {
    return;
  }
  
  serviceDetected = YES;

  dweetTimer = [NSTimer scheduledTimerWithTimeInterval:freq
                                                target:self
                                              selector:@selector(sendDweet)
                                              userInfo:nil
                                               repeats:YES];
  
  
}





- (void) sendDweet {
  
  [Dweet_ios setDebugLevel:SHOW_ALL];
  
  // send the dweet if at least one characteristic is valid
  if ([dweetData count])
    [Dweet_ios sendDweet:dweetData toThing:thingName lockedWithKey:@"" withCallback:@selector(dweetCallback:) onTarget:self overwriteData:NO];

  
}


// when the dweet send is complete, this method is called
- (void) dweetCallback:(NSArray*)rsp {

  // extract the result code
  NSInteger rslt_code = [[rsp objectAtIndex:0] integerValue];

  if (rslt_code<0) {
    // the result was an error
    statusV.backgroundColor = [UIColor redColor];
  } else {
    // the result was ok
    statusV.backgroundColor = [UIColor greenColor];
  }
  
  // flash the status indicator
  [UIView animateWithDuration:0.2 animations:^{
      statusV.alpha = 0.3;
    } completion:^(BOOL finished) {
      statusV.alpha = 1.0;
    }];
  

}
  





// helper method to convert UUID
- (NSString *)representativeString:(CBUUID*)uuid
{
  NSData *data = [uuid data];
  int bytesToConvert = (int)[data length];
  const unsigned char *uuidBytes = [data bytes];
  NSMutableString *outputString = [NSMutableString stringWithCapacity:16];
  for (int currentByteIndex = bytesToConvert-1; currentByteIndex >= 0; currentByteIndex--)
  {
    switch (currentByteIndex)
    {
      case 12:
      case 10:
      case 8:
      case 6:[outputString appendFormat:@"%02x-", uuidBytes[currentByteIndex]]; break;
      default:[outputString appendFormat:@"%02x", uuidBytes[currentByteIndex]];
    }
  }
  return [outputString uppercaseString];
}







// Table View Delegate methods
/////////////////////////////////


- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
  return [tableData count];
}

- (NSString*)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section {
  NSArray *sortedKeys = [[tableData allKeys] sortedArrayUsingSelector: @selector(caseInsensitiveCompare:)];
  return  [sortedKeys objectAtIndex:section];
}

- (CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section {
  
  return 45;
  
}

- (void)tableView:(UITableView *)tableView willDisplayHeaderView:(UIView *)view forSection:(NSInteger)section {
  UITableViewHeaderFooterView *header = (UITableViewHeaderFooterView *)view;
  header.textLabel.font = [UIFont systemFontOfSize:13];
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
  NSArray *sortedKeys = [[tableData allKeys] sortedArrayUsingSelector: @selector(caseInsensitiveCompare:)];
  NSString *s = [sortedKeys objectAtIndex:section];
  return [[tableData objectForKey:s] count];
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
  return 44;
}

- (UITableViewCell *)tableView:(UITableView *)mtableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
  
  UITableViewCell *cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleValue1 reuseIdentifier:nil];

  cell.textLabel.font = [UIFont systemFontOfSize:15];
  cell.detailTextLabel.font = [UIFont systemFontOfSize:14];
  
  NSArray *sortedServ = [[tableData allKeys] sortedArrayUsingSelector: @selector(caseInsensitiveCompare:)];
  NSString *serv = [sortedServ objectAtIndex:[indexPath section]];

  NSMutableDictionary *entry = [tableData objectForKey:serv];
  NSArray *sortedCha = [[entry allKeys] sortedArrayUsingSelector: @selector(caseInsensitiveCompare:)];
  NSString *cha = [sortedCha objectAtIndex:[indexPath row]];
  
  cell.textLabel.text = cha;
  cell.detailTextLabel.text = [entry objectForKey:cha];
  
  return cell;
  
}





- (void) hitForward {
  
  // bring up an activityViewController to share dweet link with someone
  
  NSString *textToShare = [NSString stringWithFormat:@"Follow my sensor data : https://dweet.io/follow/%@",thingName];
  
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
  return @"Sensor Data";
}



@end
