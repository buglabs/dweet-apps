//
//  ConfigController.m
//  Dweet Sensor Gateway
//
//  Created by Tim Buick on 2015-07-10.
//  Copyright (c) 2015 BugLabs. All rights reserved.
//

#import "ConfigController.h"

@interface ConfigController ()

@end

@implementation ConfigController

@synthesize navItem;
@synthesize thingInput;
@synthesize freqInput;
@synthesize thingInputLabel,tapPromptLabel;
@synthesize wrapV;

- (void)viewDidLoad {
  [super viewDidLoad];

  // create the Nav Bar
  UIView *tv = [[UIView alloc] initWithFrame:CGRectMake(0,0,80,44)];
  UIImageView *iv = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"buglogo.png"]];
  iv.frame = CGRectMake(0,12,80,18);
  [tv addSubview:iv];
  navItem.titleView = tv;

  // load the saved thing name
  savedThing = [[NSUserDefaults standardUserDefaults] objectForKey:@"thing"];
  thingInput.text = savedThing;

  // change the label text if the thing name has been customized
  if (![savedThing isEqualToString:[[NSUserDefaults standardUserDefaults] objectForKey:@"randomized-thing"]]) {
    thingInputLabel.text = @"current thing name";
  }

  // set the segmented control based on the saved frequency value
  freq = [[[NSUserDefaults standardUserDefaults] objectForKey:@"freq"] floatValue];
  if (freq==0.5) {
    [freqInput setSelectedSegmentIndex:0];
  } else if (freq==1.0) {
    [freqInput setSelectedSegmentIndex:1];
  } else if (freq==2.0) {
    [freqInput setSelectedSegmentIndex:2];
  } else if (freq==5.0) {
    [freqInput setSelectedSegmentIndex:3];
  } else {
    // should never get here
    [freqInput setSelectedSegmentIndex:1];
    [[NSUserDefaults standardUserDefaults] setObject:@(1.0) forKey:@"freq"];
  }
  
}

- (void) viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  
  // adjust the layout based on device screen size
  CGRect screenBounds = [[UIScreen mainScreen] bounds];
  wrapV.frame = CGRectMake((screenBounds.size.width-320)/2,0,320,800);

}

- (void)didReceiveMemoryWarning {
  [super didReceiveMemoryWarning];
}

- (void) viewDidAppear:(BOOL)animated {
  [super viewDidAppear:animated];
  
}


- (IBAction) freqChanged {
  
  // update the saved dweet frequency
  if ([freqInput selectedSegmentIndex]==0) [[NSUserDefaults standardUserDefaults] setObject:@(0.5) forKey:@"freq"];
  if ([freqInput selectedSegmentIndex]==1) [[NSUserDefaults standardUserDefaults] setObject:@(1.0) forKey:@"freq"];
  if ([freqInput selectedSegmentIndex]==2) [[NSUserDefaults standardUserDefaults] setObject:@(2.0) forKey:@"freq"];
  if ([freqInput selectedSegmentIndex]==3) [[NSUserDefaults standardUserDefaults] setObject:@(5.0) forKey:@"freq"];
  
}





// UITextField delefates

- (BOOL)textField:(UITextField *)textField shouldChangeCharactersInRange:(NSRange)range replacementString:(NSString *)string {
  
  
  NSString *check = [textField.text stringByReplacingCharactersInRange:range withString:string];
  check = [check uppercaseString];
  
  // only allow certain ASCII chars in a thing name
  for (int i=0;i<[check length];i++) {
    if ([check characterAtIndex:i]<33 || [check characterAtIndex:i]>126)
      return false;
  }
  
  tapPromptLabel.text = @"thing name updated";
  
  // save the new thing name
  savedThing = textField.text;
  [[NSUserDefaults standardUserDefaults] setObject:savedThing forKey:@"thing"];
  
  // adjust the label text
  if (![savedThing isEqualToString:[[NSUserDefaults standardUserDefaults] objectForKey:@"randomized-thing"]]) {
    thingInputLabel.text = @"current thing name";
  }
  
  return true;
  
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
  [textField resignFirstResponder];
  
  
  return true;
}





@end
