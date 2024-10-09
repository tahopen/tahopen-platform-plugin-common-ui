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
  "pentaho/lang/UserError"
], function(UserError) {
  "use strict";

  /* global describe:false, it:false, expect:false, beforeEach:false */

  describe("pentaho.lang.UserError -", function() {
    it("should be defined.", function() {
      expect(typeof UserError).toBeDefined();
    });

    var error;

    beforeEach(function() {
      error = new UserError();
    });

    it("name property should be \"UserError\"", function() {
      expect(error.name).toBe("UserError");
    });

    it("name property should be read-only", function() {
      expect(function() {
        error.name = "New Name";
      }).toThrowError(TypeError);
    });

  }); // #pentaho.lang.UserError
});
