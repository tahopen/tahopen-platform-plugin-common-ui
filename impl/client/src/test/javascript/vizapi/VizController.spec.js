/*!
 * Copyright 2010 - 2017 Hitachi Vantara.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
define([
  "common-ui/vizapi/VizController"
], function() {

  describe("VizController", function() {

    describe("getRgbGradientFromMultiColorHex", function() {

      it("should return a proper color from gradient by value", function() {
        var min = 10;
        var max = 100;
        var colors = ["#FF0000","#FFBF3F","#FFFF00","#BFDF3F","#008000"];

        expect( pentaho.VizController.getRgbGradientFromMultiColorHex( 10, min, max, colors ) ).toBe( "RGB(255,0,0)" );
        expect( pentaho.VizController.getRgbGradientFromMultiColorHex( 11, min, max, colors ) ).toBe( "RGB(255,8,2)" );
        expect( pentaho.VizController.getRgbGradientFromMultiColorHex( 20, min, max, colors ) ).toBe( "RGB(255,84,28)" );
        expect( pentaho.VizController.getRgbGradientFromMultiColorHex( 50, min, max, colors ) ).toBe( "RGB(255,240,14)" );
        expect( pentaho.VizController.getRgbGradientFromMultiColorHex( 51, min, max, colors ) ).toBe( "RGB(255,243,11)" );
        expect( pentaho.VizController.getRgbGradientFromMultiColorHex( 99, min, max, colors ) ).toBe( "RGB(8,132,2)" );
        expect( pentaho.VizController.getRgbGradientFromMultiColorHex( 100, min, max, colors ) ).toBe( "RGB(0,128,0)" );
      });
    });
  });
});
