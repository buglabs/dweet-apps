//
//  Dweet_ios.h
//  Dweet-ios
//
//  Created by Tim Buick on 2015-06-10.
//  Copyright (c) 2015 PBJ Studios. All rights reserved.
//

#import <Foundation/Foundation.h>


#define DDBG(x,...) if (debugLevel!=0 && x<=debugLevel) NSLog(__VA_ARGS__)



// debug levels
enum {
  NO_DEBUG=0,
  SHOW_ERRORS=1,
  SHOW_ALL=2,
};

// return codes
enum {
  DWEET_STILL_PENDING=1,
  DWEET_SUCCESS=0,
  NO_NETWORK=-1,
  COULD_NOT_CONNECT_TO_DWEETIO=-2,
  DWEET_DID_NOT_RETURN_VALID_JSON=-3,
  DWEET_JSON_FORMAT_UNEXPECTED=-4,
  DWEET_RESPONSE_IS_FAILED=-5,
  COULD_NOT_CONNECT_TO_LOCKED_THING=-6,
  COULD_NOT_GENERATE_JSON_FROM_DATA=-7,
  CONNECTION_ERROR=-8,
  
  
  NOT_CONNECTED_TO_THING=-1000,

};


@interface Dweet_ios : NSObject <NSURLConnectionDelegate> {
  
  NSString *thingName;
  NSMutableDictionary *thingProcesses;
  NSMutableDictionary *thingProcessData;
  NSMutableDictionary *thingCallbacks;
  NSMutableDictionary *thingCallbackTargets;

}


// global shared instance for Dweet_ios object
+ (Dweet_ios *)sharedInstance;

// set the NSLog debug level using enum from above
+ (void) setDebugLevel:(NSInteger)level;





+(void)sendDweet:(NSDictionary*)data toThing:(NSString*)thing lockedWithKey:(NSString*)key withCallback:(SEL)callback onTarget:(id)target overwriteData:(BOOL)overwrite;




// currently connected thing
@property (nonatomic, retain) NSString *thingName;
@property (nonatomic, retain) NSMutableDictionary *thingProcesses;
@property (nonatomic, retain) NSMutableDictionary *thingProcessData;
@property (nonatomic, retain) NSMutableDictionary *thingCallbacks;
@property (nonatomic, retain) NSMutableDictionary *thingCallbackTargets;




@end
