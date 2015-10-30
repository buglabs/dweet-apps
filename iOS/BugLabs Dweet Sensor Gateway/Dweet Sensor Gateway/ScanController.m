//
//  ViewController.m
//  Dweet Sensor Gateway
//
//  Created by Tim Buick on 2015-07-09.
//  Copyright (c) 2015 BugLabs. All rights reserved.
//

#import "ScanController.h"

#import "DDMathParser.h"


@interface ScanController ()

@end

@implementation ScanController

@synthesize scanV;
@synthesize deviceTableV;
@synthesize navItem;
@synthesize shadedV;

- (void)viewDidLoad {
  [super viewDidLoad];
  
  // recover the saved thing name
  savedThing = [[NSUserDefaults standardUserDefaults] objectForKey:@"thing"];
  
  
  
  // initial copy of BlueMS1.txt and BlueNRG to documents directory
  //////////////////////
  NSString *documentsDirectory = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex:0];
  {
    NSString *destPath = [documentsDirectory stringByAppendingString:@"/BlueMS1.txt"];
    NSString *sourcePath = [[[NSBundle mainBundle] resourcePath] stringByAppendingPathComponent:@"BlueMS1.txt"];
    [[NSFileManager defaultManager] removeItemAtPath:destPath error:nil];
    [[NSFileManager defaultManager] copyItemAtPath:sourcePath toPath:destPath error:nil];
    NSLog(@"Source Path: %@\n Dest Path: %@", sourcePath, destPath);
  }
  {
    NSString *destPath = [documentsDirectory stringByAppendingString:@"/Blue6180.txt"];
    NSString *sourcePath = [[[NSBundle mainBundle] resourcePath] stringByAppendingPathComponent:@"Blue6180.txt"];
    [[NSFileManager defaultManager] removeItemAtPath:destPath error:nil];
    [[NSFileManager defaultManager] copyItemAtPath:sourcePath toPath:destPath error:nil];
    NSLog(@"Source Path: %@\n Dest Path: %@", sourcePath, destPath);
  }

  deviceConfig = [[NSMutableDictionary alloc] init];
  
  
  // load all config files from documents directory
  //////////////////////
  NSArray *files = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:documentsDirectory error:nil];
  for (NSString *file in files) {
    NSLog(@"Parsing:%@",file);
    NSString *fn = [documentsDirectory stringByAppendingPathComponent:file];
    NSString *contents = [NSString stringWithContentsOfFile:fn encoding:NSASCIIStringEncoding error:nil];
    NSLog(@"-----------");
    
    NSData *data = [contents dataUsingEncoding:NSUTF8StringEncoding];
    id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];

    NSLog(@"devName:%@",[json objectForKey:@"devName"]);
    NSLog(@"%lu services found",(unsigned long)[[json objectForKey:@"services"] count]);
    
    NSLog(@"-----------");

    [deviceConfig setObject:json forKey:[json objectForKey:@"devName"]];
    
  }
  
  
  
  /////////////////
  // remove the next line if you don't
  // want a new random thing name each time app starts
  savedThing = nil;
  /////////////////
  
  // there is no saved thing name, generate a new random name
  if (!savedThing) {
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
  
  
  // initialize table data sources
  knownDeviceList = [NSMutableArray array];
  unknownDeviceList = [NSMutableArray array];
  
  // initialize BLTE manager
  BtManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
  
  // create the Nav Bar
  UIView *tv = [[UIView alloc] initWithFrame:CGRectMake(0,0,80,44)];
  UIImageView *iv = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"buglogo.png"]];
  iv.frame = CGRectMake(0,12,80,18);
  [tv addSubview:iv];
  navItem.titleView = tv;
  [self.navigationController.navigationBar setBarTintColor:[UIColor darkGrayColor]];
  [self.navigationController.navigationBar setTranslucent:NO];
  [self.navigationController.navigationBar setTintColor:[UIColor whiteColor]];
  
  firstAutoScan = 0;
  poweredOn = 0;
  
  
  DDMathEvaluator *evaluator = [DDMathEvaluator defaultMathEvaluator];
  [evaluator registerFunction:^DDExpression *(NSArray *args, NSDictionary *vars, DDMathEvaluator *eval, NSError *__autoreleasing *error) {
    if ([args count] == 3) {
      DDExpression *condition = [args objectAtIndex:0];
      DDExpression *resultExpression = nil;
//      NSNumber *conditionValue = [condition evaluateWithSubstitutions:vars evaluator:eval error:error];
      NSNumber *conditionValue = [eval evaluateExpression:condition withSubstitutions:vars error:error];
      if ([conditionValue boolValue] == YES) {
        resultExpression = [args objectAtIndex:1];
      } else {
        resultExpression = [args objectAtIndex:2];
      }
//      NSNumber *result = [resultExpression evaluateWithSubstitutions:vars evaluator:eval error:error];
      NSNumber *result = [eval evaluateExpression:resultExpression withSubstitutions:vars error:error];
      return [DDExpression numberExpressionWithNumber:result];
    }
    return nil;
    
  } forName:@"if"];
  
}

- (void) viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  
  // adjust layout for different devices
  CGRect screenBounds = [[UIScreen mainScreen] bounds];
  deviceTableV.frame = CGRectMake(0,0,screenBounds.size.width,screenBounds.size.height);
  shadedV.center = CGPointMake(screenBounds.size.width/2, screenBounds.size.height/2);

}

- (void)didReceiveMemoryWarning {
  [super didReceiveMemoryWarning];
}






- (IBAction) doBtScan {

  NSLog(@"in doBtScan");
  
  // start int a new BTLE scan, clear out table view datasource
  [knownDeviceList removeAllObjects];
  [unknownDeviceList removeAllObjects];
  [deviceTableV reloadData];

  // the BTLE device is not powered, just return
  if (!poweredOn) return;
  
  // show an activity spinner while scanning
  UIActivityIndicatorView *spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhite];
  spinner.hidesWhenStopped = YES;
  [spinner startAnimating];
  navItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithCustomView:spinner];
  
  // stop any existing scan
  [BtManager stopScan];
  
  // start scan
  [BtManager scanForPeripheralsWithServices:nil options:nil];
  
  
  
}



- (IBAction) configHit {
  
  // bring up dweet config view
  ConfigController *cc = (ConfigController*)[self.storyboard instantiateViewControllerWithIdentifier:@"ConfigController"];
  [self.navigationController pushViewController:cc animated:YES];
  
}







// Table View Delegate methods
/////////////////////////////////


- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
  return 2;
}

- (NSString*)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section {
  if (section==0) {
    return @"Known Devices";
  } else {
    return @"Unknown Devices";
  }
}

- (void)tableView:(UITableView *)tableView willDisplayHeaderView:(UIView *)view forSection:(NSInteger)section
{
  UITableViewHeaderFooterView *v = (UITableViewHeaderFooterView *)view;
  v.backgroundView.backgroundColor = [UIColor lightGrayColor];
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
  if (section==0) {
    return [knownDeviceList count];
  }
  else {
    return [unknownDeviceList count];
  }
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
  if ([indexPath section]==0) {
    return 60;
  } else {
    return 40;
  }
}

- (UITableViewCell *)tableView:(UITableView *)mtableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
  
  UITableViewCell *cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:nil];
  
  if ([indexPath section]==0) {
    
    // known devices
    // include a logo image
    
    NSArray *device = [knownDeviceList objectAtIndex:[indexPath row]];
    CBPeripheral *p = [device objectAtIndex:0];
    
    UILabel *name = [[UILabel alloc] initWithFrame:CGRectMake(54,9,200,30)];
    name.text = [p name];
    name.font = [UIFont boldSystemFontOfSize:20];
    name.textColor = [UIColor blackColor];
    name.backgroundColor = [UIColor clearColor];
    [cell.contentView addSubview:name];
    UILabel *addr = [[UILabel alloc] initWithFrame:CGRectMake(54,28,250,30)];
    addr.text = [[p identifier] UUIDString];
    addr.font = [UIFont boldSystemFontOfSize:11];
    addr.textColor = [UIColor darkGrayColor];
    addr.backgroundColor = [UIColor clearColor];
    [cell.contentView addSubview:addr];
    UIImageView *logo = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"stlogo"]];
    logo.frame = CGRectMake(3,20,46,24);
    [cell.contentView addSubview:logo];
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    cell.selectionStyle = UITableViewCellSelectionStyleDefault;
    UIView *bgColorView = [[UIView alloc] init];
    bgColorView.backgroundColor = [UIColor cyanColor];
    [cell setSelectedBackgroundView:bgColorView];
  } else {
    
    // unknown devices, keep it simple.
    
    NSArray *device = [unknownDeviceList objectAtIndex:[indexPath row]];
    CBPeripheral *p = [device objectAtIndex:0];
    if ([p name]) {
      cell.textLabel.text = [p name];
    } else {
      cell.textLabel.text = @"no name";
    }
    cell.textLabel.font = [UIFont italicSystemFontOfSize:17];
    cell.selectionStyle = UITableViewCellSelectionStyleNone;
  }
  
  return cell;
  
}


- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
  
  [tableView deselectRowAtIndexPath:indexPath animated:YES];
  
  if ([indexPath section]==0) {

    // connect to the selected device
    NSArray *device = [knownDeviceList objectAtIndex:[indexPath row]];
    CBPeripheral *p = [device objectAtIndex:0];
    [BtManager connectPeripheral:p options:nil];
    
    // add shaded overlay so no more screen activity can take place
    UIView *overlay = [[UIView alloc] initWithFrame:self.navigationController.view.frame];
    overlay.backgroundColor = [UIColor blackColor];
    overlay.alpha = 0.3;
    overlay.tag = 12345;  // set a tag so we can get it back later
    UIActivityIndicatorView *spin = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhiteLarge];
    spin.frame = CGRectMake(142,265,37,37);
    [overlay addSubview:spin];
    [spin startAnimating];
    [self.navigationController.view addSubview:overlay];
    
  }
  
}


// CoreBluetooth Delegate methods
/////////////////////////////////

-(void) centralManagerDidUpdateState:(CBCentralManager *)central {
  NSLog(@"centralManagerDidUpdateState");
  
  if (central.state==CBCentralManagerStatePoweredOff) {
    NSLog(@" PoweredOff");
  } else if (central.state==CBCentralManagerStatePoweredOn) {
    NSLog(@" PoweredOn");
  } else {
    NSLog(@" Other");
  }
  
  // BTLE was powered on, but now is not. Show a popup.
  if (poweredOn==1 && central.state==CBCentralManagerStatePoweredOff) {
    [self deviceDisconnect];
  }
  
  // track the power on state of BTLE
  if (central.state==CBCentralManagerStatePoweredOn) {
    poweredOn=1;
  } else {
    poweredOn=0;
  }

  // when the app first starts, and BTLE powers on,
  // automatically do a BTLE scan
  if (firstAutoScan==0 && central.state==CBCentralManagerStatePoweredOn) {
    firstAutoScan=1;
    [self doBtScan];
  }
  
}

-(void) centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advertisementData RSSI:(NSNumber *)RSSI {
  NSLog(@"centralManager:didDiscoverPeripheral:%@",[peripheral name]);

  // we don't want duplicate devices in the datasource
  for (NSArray *ar in knownDeviceList) {
    CBPeripheral *p = [ar objectAtIndex:0];
    if ([[[p identifier] UUIDString] isEqualToString:[[peripheral identifier] UUIDString]]) {
      NSLog(@"duplicate known peripheral");
      return;
    }
  }
  for (NSArray *ar in unknownDeviceList) {
    CBPeripheral *p = [ar objectAtIndex:0];
    if ([[[p identifier] UUIDString] isEqualToString:[[peripheral identifier] UUIDString]]) {
      NSLog(@"duplicate unknown peripheral");
      return;
    }
  }
  
  // check for known devices, in this case only BlueMS
  if ([peripheral name] && [[peripheral name] length]>0) {
    int found=0;
    for (NSString *name in [deviceConfig allKeys]) {
      if ([[peripheral name] isEqualToString:name]) {
        found=1;
        [knownDeviceList addObject:[NSArray arrayWithObjects:peripheral,advertisementData,nil]];
      }
    }
    if (!found) {
      [unknownDeviceList addObject:[NSArray arrayWithObjects:peripheral,advertisementData,nil]];
    }
  } else {
    [unknownDeviceList addObject:[NSArray arrayWithObjects:peripheral,advertisementData,nil]];
  }
  
  // once we find a device, stop that activity spinner
  navItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemRefresh target:self action:@selector(doBtScan)];
  [deviceTableV reloadData];
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral {
  NSLog(@"centralManager:didConnectPeripheral");

  // stop the activity spinner
  navItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemRefresh target:self action:@selector(doBtScan)];

  // remove the shaded overlay
  UIView *overlay = [self.navigationController.view viewWithTag:12345];
  [overlay removeFromSuperview];
  
  // push the sensor detail view
  DweetController *dc = (DweetController*)[self.storyboard instantiateViewControllerWithIdentifier:@"DweetController"];
  dc.connectedPeripheral = peripheral;
  dc.deviceConfigJson = [deviceConfig objectForKey:[peripheral name]];
  dc.connectedPeripheral.delegate = dc;
  [self.navigationController pushViewController:dc animated:YES];
  
   
}

- (void)centralManager:(CBCentralManager *)central didFailToConnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error {
  NSLog(@"centralManager:didFailToConnectPeripheral");

  // stop the activity spinner
  navItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemRefresh target:self action:@selector(doBtScan)];

  UIAlertView *av = [[UIAlertView alloc] initWithTitle:@"Connection Error" message:nil delegate:nil cancelButtonTitle:@"ok" otherButtonTitles:nil];
  [av show];
}

- (void)centralManager:(CBCentralManager *)central didRetrieveConnectedPeripherals:(NSArray *)peripherals {
  NSLog(@"centralManager:didRetrieveConnectedPeripherals");
}

- (void)centralManager:(CBCentralManager *)central didRetrievePeripherals:(NSArray *)peripherals {
  NSLog(@"centralManager:didRetrievePeripherals");
}

- (void)centralManager:(CBCentralManager *)central didDisconnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error {
  NSLog(@"centralManager:didDisconnectPeripheral:%@",[peripheral name]);
  // show disconnect popup
  [self deviceDisconnect];
}





- (void) deviceDisconnect {

  // show a popup if the view shown is a sensor detail view
  UIViewController *vc = [self.navigationController visibleViewController];
  if ([vc isKindOfClass:[DweetController class]]) {
    [self.navigationController popToRootViewControllerAnimated:YES];
    [knownDeviceList removeAllObjects];
    [unknownDeviceList removeAllObjects];
    [deviceTableV reloadData];
  }
  UIAlertView *av = [[UIAlertView alloc] initWithTitle:@"Device Disconnected" message:nil delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil];
  [av show];
}




@end
