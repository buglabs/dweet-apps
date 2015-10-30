//
//  Dweet_ios.m
//  Dweet-ios
//
//  Created by Tim Buick on 2015-06-10.
//  Copyright (c) 2015 PBJ Studios. All rights reserved.
//

#import "Dweet_ios.h"

#import "Reachability.h"

@implementation Dweet_ios

@synthesize thingProcesses;
@synthesize thingProcessData;
@synthesize thingCallbacks;
@synthesize thingCallbackTargets;

static Dweet_ios *dweetInstance = nil;
static NSInteger debugLevel = NO_DEBUG;

+ (Dweet_ios *) sharedInstance {
  if (!dweetInstance) {
    dweetInstance = [[Dweet_ios alloc] init];
    dweetInstance.thingProcesses = [[NSMutableDictionary alloc] init];
    dweetInstance.thingProcessData = [[NSMutableDictionary alloc] init];
    dweetInstance.thingCallbacks = [[NSMutableDictionary alloc] init];
    dweetInstance.thingCallbackTargets = [[NSMutableDictionary alloc] init];
  }
  return dweetInstance;
}


+ (void) setDebugLevel:(NSInteger)level {
  debugLevel = level;
}






+(void)sendDweet:(NSDictionary*)data toThing:(NSString*)thing lockedWithKey:(NSString*)key withCallback:(SEL)callback onTarget:(id)target overwriteData:(BOOL)overwrite {

  NSError *err;

  
  // check network status
  // ------------
  Reachability *reachability = [Reachability reachabilityForInternetConnection];
  NetworkStatus networkStatus = [reachability currentReachabilityStatus];
  if (networkStatus==NotReachable) {
    DDBG(SHOW_ERRORS,@"ERROR %d",NO_NETWORK);
    if (target && callback) {
      NSArray *ar = [NSArray arrayWithObjects:@(NO_NETWORK),@"",nil];
      [target performSelector:callback withObject:ar afterDelay:0];
    }
    return;
  }

  
  // create the shared Dweet instance
  // -----------
  Dweet_ios *shared = [self sharedInstance];
  
  NSString *urlString = [NSString stringWithFormat:@"https://dweet.io/dweet/for/%@",thing];
  
  NSURLConnection *conn = [[shared thingProcesses] objectForKey:urlString];
  NSData *connData = [[shared thingProcessData] objectForKey:urlString];

  if (conn && connData) {
    // the connection exists.. do we want to overwrite and
    // start a new connection?
    if (overwrite) {
      DDBG(SHOW_ALL,@"connection exists, but do an overwrite");
      [conn cancel];
      [[shared thingProcesses] removeObjectForKey:urlString];
      [[shared thingProcessData] removeObjectForKey:urlString];
      [[shared thingCallbackTargets] removeObjectForKey:urlString];
      [[shared thingCallbacks] removeObjectForKey:urlString];
    } else {
      DDBG(SHOW_ALL,@"connection still going");
      if (target && callback) {
        NSArray *ar = [NSArray arrayWithObjects:@(DWEET_STILL_PENDING),@"",nil];
        [target performSelector:callback withObject:ar afterDelay:0];
      }
      return;
    }
  } else {
    // this is the first time we've seen this thing, create an NSURLConnection for it
    DDBG(SHOW_ALL,@"new thing, create connection");
  }

  // build json from provided data
  // ----------------
  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:data
                                                     options:NSJSONWritingPrettyPrinted
                                                       error:&err];
  if (!jsonData) {
    DDBG(SHOW_ERRORS,@"ERROR %d : %@",COULD_NOT_GENERATE_JSON_FROM_DATA,err.localizedDescription);
    if (target && callback) {
      NSArray *ar = [NSArray arrayWithObjects:@(COULD_NOT_GENERATE_JSON_FROM_DATA),@"",nil];
      [target performSelector:callback withObject:ar afterDelay:0];
    }
    return;
  }
  
  // create POST request
  // --------------
  NSURL *url = [NSURL URLWithString:urlString];
  DDBG(SHOW_ALL,@"URL : %@",urlString);
  
  NSString *postLength = [NSString stringWithFormat:@"%lu", (unsigned long)[jsonData length]];
  
  NSMutableURLRequest *request = [[NSMutableURLRequest alloc] init];
  [request setURL:url];
  [request setHTTPMethod:@"POST"];
  [request setValue:postLength forHTTPHeaderField:@"Content-Length"];
  [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
  [request setHTTPBody:jsonData];
  
  // create NSURLConnection
  // -----------------------
  NSURLConnection *newConn = [[NSURLConnection alloc] initWithRequest:request delegate:shared];
  [[shared thingProcesses] setObject:newConn forKey:urlString];
  NSMutableData *rspData = [[NSMutableData alloc] init];
  [[shared thingProcessData] setObject:rspData forKey:urlString];
  if (target && callback) {
    [[shared thingCallbackTargets] setObject:target forKey:urlString];
    NSValue* sel = [NSValue valueWithPointer:callback];
    [[shared thingCallbacks] setObject:sel forKey:urlString];
  }
  [conn start];
  
}





// NSURLConnection delegates


- (void)connection:(NSURLConnection *)connection didReceiveResponse:(NSURLResponse *)response {
  NSHTTPURLResponse *httpRsp = (NSHTTPURLResponse*)response;
  DDBG(SHOW_ALL,@"connection didRecvRsp:%@:%ld",connection.currentRequest.URL,(long)httpRsp.statusCode);
  Dweet_ios *this = [Dweet_ios sharedInstance];
  NSString *thingUrl = [[[connection currentRequest] URL] absoluteString];

  id target = [[this thingCallbackTargets] objectForKey:thingUrl];
  SEL selector = [[[this thingCallbacks] objectForKey:thingUrl] pointerValue];

  if (httpRsp.statusCode!=200) {
    [[this thingProcesses] removeObjectForKey:thingUrl];
    [[this thingProcessData] removeObjectForKey:thingUrl];
    [[this thingCallbackTargets] removeObjectForKey:thingUrl];
    [[this thingCallbacks] removeObjectForKey:thingUrl];
    
    if (target && selector) {
      NSArray *ar = [NSArray arrayWithObjects:@(COULD_NOT_CONNECT_TO_DWEETIO),@"",nil];
      [target performSelector:selector withObject:ar afterDelay:0];
    }
    return;

  }
  
}

- (void)connection:(NSURLConnection *)connection didReceiveData:(NSData *)data {
  Dweet_ios *this = [Dweet_ios sharedInstance];
  NSMutableData *rspData = [[this thingProcessData] objectForKey:[[[connection currentRequest] URL] absoluteString]];
  if (!rspData) {
    // TODO error handling!
    return;
  }
  [rspData appendData:data];
}

- (NSCachedURLResponse *)connection:(NSURLConnection *)connection willCacheResponse:(NSCachedURLResponse*)cachedResponse {
  return nil;
}

- (void)connectionDidFinishLoading:(NSURLConnection *)connection {
  DDBG(SHOW_ALL,@"connectionDidFinish(%p):%@",connection,connection.currentRequest.URL);
  Dweet_ios *this = [Dweet_ios sharedInstance];
  NSString *thingUrl = [[[connection currentRequest] URL] absoluteString];
  NSData *rspData = [[this thingProcessData] objectForKey:thingUrl];
  NSString *string = [[NSString alloc] initWithData:rspData encoding:NSUTF8StringEncoding];
  DDBG(SHOW_ALL,@"%@",string);
  
  
  // try to extract JSON data from dweet response
  // -----------------
  NSError *err;
  id json = [NSJSONSerialization JSONObjectWithData:rspData options:0 error:&err];
  
  id target = [[this thingCallbackTargets] objectForKey:thingUrl];
  SEL selector = [[[this thingCallbacks] objectForKey:thingUrl] pointerValue];
  
  
  // clear context
  ////////////////
  [[this thingProcesses] removeObjectForKey:thingUrl];
  [[this thingProcessData] removeObjectForKey:thingUrl];
  [[this thingCallbackTargets] removeObjectForKey:thingUrl];
  [[this thingCallbacks] removeObjectForKey:thingUrl];
  
  
  
  // return codes if callback present
  //////////////////
  if (!json) {
    DDBG(SHOW_ERRORS,@"ERROR %d : %@",DWEET_DID_NOT_RETURN_VALID_JSON,err.localizedDescription);
    if (target && selector) {
      NSArray *ar = [NSArray arrayWithObjects:@(DWEET_DID_NOT_RETURN_VALID_JSON),@"",nil];
      [target performSelector:selector withObject:ar afterDelay:0];
    }
    return;
  }
  
  if ([json isKindOfClass:[NSArray class]]) {
    DDBG(SHOW_ERRORS,@"ERROR %d : JSON root is an ARRAY",DWEET_JSON_FORMAT_UNEXPECTED);
    if (target && selector) {
      NSArray *ar = [NSArray arrayWithObjects:@(DWEET_JSON_FORMAT_UNEXPECTED),@"",nil];
      [target performSelector:selector withObject:ar afterDelay:0];
    }
    return;
  } else if ([json isKindOfClass:[NSDictionary class]]) {
    // a dweet must have a dictionary at the json root
    NSDictionary *dweet_rsp = (NSDictionary*)json;
    
    // every dweet must have a 'this' response
    if (![dweet_rsp objectForKey:@"this"]) {
      DDBG(SHOW_ERRORS,@"ERROR %d : key 'this' not found in JSON",DWEET_JSON_FORMAT_UNEXPECTED);
      if (target && selector) {
        NSArray *ar = [NSArray arrayWithObjects:@(DWEET_JSON_FORMAT_UNEXPECTED),@"",nil];
        [target performSelector:selector withObject:ar afterDelay:0];
      }
      return;
    } else if ([[dweet_rsp objectForKey:@"this"] isEqualToString:@"succeeded"]) {
      // thing already exists... ok.
    } else if ([[dweet_rsp objectForKey:@"this"] isEqualToString:@"failed"]) {
      DDBG(SHOW_ERRORS,@"ERROR %d : Dweet response is FAILED",DWEET_RESPONSE_IS_FAILED);
      if (target && selector) {
        NSArray *ar = [NSArray arrayWithObjects:@(DWEET_RESPONSE_IS_FAILED),@"",nil];
        [target performSelector:selector withObject:ar afterDelay:0];
      }
      return;
    } else {
      DDBG(SHOW_ERRORS,@"ERROR %d : unexpected 'this' value found in JSON",DWEET_JSON_FORMAT_UNEXPECTED);
      if (target && selector) {
        NSArray *ar = [NSArray arrayWithObjects:@(DWEET_JSON_FORMAT_UNEXPECTED),@"",nil];
        [target performSelector:selector withObject:ar afterDelay:0];
      }
      return;
    }
  } else {
    DDBG(SHOW_ERRORS,@"ERROR %d : JSON root is not ARRAY or DICT",DWEET_DID_NOT_RETURN_VALID_JSON);
    if (target && selector) {
      NSArray *ar = [NSArray arrayWithObjects:@(DWEET_DID_NOT_RETURN_VALID_JSON),@"",nil];
      [target performSelector:selector withObject:ar afterDelay:0];
    }
    return;
  }
  
  
  if (target && selector) {
    NSArray *ar = [NSArray arrayWithObjects:@(DWEET_SUCCESS),string,nil];
    [target performSelector:selector withObject:ar afterDelay:0];
  }

  
  

}

- (void)connection:(NSURLConnection *)connection didFailWithError:(NSError *)error {
  Dweet_ios *this = [Dweet_ios sharedInstance];
  NSString *thingUrl = [[[connection currentRequest] URL] absoluteString];
  
  id target = [[this thingCallbackTargets] objectForKeyedSubscript:thingUrl];
  SEL selector = [[[this thingCallbacks] objectForKey:thingUrl] pointerValue];
  if (target && selector) {
    NSArray *ar = [NSArray arrayWithObjects:@(CONNECTION_ERROR),@"",nil];
    [target performSelector:selector withObject:ar afterDelay:0];
  }

  [connection cancel];
  [[this thingProcesses] removeObjectForKey:thingUrl];
  [[this thingProcessData] removeObjectForKey:thingUrl];
  [[this thingCallbackTargets] removeObjectForKey:thingUrl];
  [[this thingCallbacks] removeObjectForKey:thingUrl];


}




















@end
