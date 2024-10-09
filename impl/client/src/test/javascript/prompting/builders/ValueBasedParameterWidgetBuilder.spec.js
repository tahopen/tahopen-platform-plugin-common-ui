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
 *
 */
define(['common-ui/prompting/builders/ValueBasedParameterWidgetBuilder'], function(ValueBasedParameterWidgetBuilder) {

  describe("ValueBasedParameterWidgetBuilder", function() {

    var args;

    var valueBasedParameterWidgetBuilder;

    beforeEach(function() {
      args = {
        promptPanel: {
          generateWidgetGUID: function() { },
          getParameterName: function() { }
        },
        param:  {
          values: []
        }
      };

      valueBasedParameterWidgetBuilder = new ValueBasedParameterWidgetBuilder();
    });

    it("should throw an error building component with no parameters", function() {
      expect(valueBasedParameterWidgetBuilder.build).toThrow();
    });

    it("should return build successfully", function() {
      var component = valueBasedParameterWidgetBuilder.build(args);
      expect(component.valuesArray.length == 0).toBeTruthy();
    });

    it("should return build successfully and have a values array", function() {
      args.param.values = [
        { label: 'test 1', value: 'value test 1' },
        { label: 'test 2', value: 'value test 2' }
      ];
      var component = valueBasedParameterWidgetBuilder.build(args);
      expect(component.valuesArray.length).toBe(2);
    });

  });

});
