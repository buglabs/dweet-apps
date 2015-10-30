//
//  _DDVariableExpression.m
//  DDMathParser
//
//  Created by Dave DeLong on 11/18/10.
//  Copyright 2010 Home. All rights reserved.
//

#import "DDMathParser.h"
#import "_DDVariableExpression.h"
#import "DDMathEvaluator.h"
#import "DDMathEvaluator+Private.h"
#import "DDMathParserMacros.h"

@implementation _DDVariableExpression {
	NSString *_variable;
    BOOL _hasSpace;
}

- (id)initWithVariable:(NSString *)v {
	self = [super init];
	if (self) {
        if ([v hasPrefix:@"$"]) {
            v = [v substringFromIndex:1];
        }
        if ([v length] == 0) {
            return nil;
        }
		_variable = [v copy];
        _hasSpace = ([_variable rangeOfCharacterFromSet:[NSCharacterSet whitespaceCharacterSet]].location != NSNotFound);
	}
	return self;
}

- (id)initWithCoder:(NSCoder *)aDecoder {
    return [self initWithVariable:[aDecoder decodeObjectForKey:@"variable"]];
}

- (void)encodeWithCoder:(NSCoder *)aCoder {
    [aCoder encodeObject:[self variable] forKey:@"variable"];
}

- (id)copyWithZone:(NSZone *)zone {
#pragma unused(zone)
    return [[[self class] alloc] initWithVariable:[self variable]];
}

- (DDExpressionType)expressionType { return DDExpressionTypeVariable; }

- (NSString *)variable { return _variable; }

- (DDExpression *)simplifiedExpressionWithEvaluator:(DDMathEvaluator *)evaluator error:(NSError **)error {
#pragma unused(evaluator, error)
	return self;
}

- (NSString *)description {
    NSString *quote = _hasSpace ? @"\"" : @"";
	return [NSString stringWithFormat:@"$%@%@%@", quote, [self variable], quote];
}

@end
