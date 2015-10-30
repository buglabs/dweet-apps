//
//  ConfigController.h
//  Dweet Sensor Gateway
//
//  Created by Tim Buick on 2015-07-10.
//  Copyright (c) 2015 BugLabs. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface ConfigController : UIViewController <UITextFieldDelegate> {
  
  NSString *savedThing;
  float freq;
  
}


@property (nonatomic, retain) IBOutlet UINavigationItem *navItem;
@property (nonatomic, retain) IBOutlet UITextField *thingInput;
@property (nonatomic, retain) IBOutlet UISegmentedControl *freqInput;
@property (nonatomic, retain) IBOutlet UILabel *thingInputLabel;
@property (nonatomic, retain) IBOutlet UILabel *tapPromptLabel;

@property (nonatomic, retain) IBOutlet UIView *wrapV;

@end
