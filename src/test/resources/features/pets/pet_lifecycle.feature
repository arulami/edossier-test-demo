Feature: Pet lifecycle
  In order to keep the catalogue accurate
  As a store clerk
  I want to register pets and keep their status up to date

  Scenario: Registering a new pet
    When Clara registers a pet named "Rex" with status "available"
    Then the pet should be stored with name "Rex" and status "available"

  Scenario: Selling a pet moves it between status lists
    Given Clara has registered a pet named "Milo" with status "available"
    When she marks the pet as "sold"
    Then the pet should appear in the "sold" list
    And the pet should not appear in the "available" list

  Scenario Outline: Searching with an unknown status is rejected
    When Clara searches for pets with status "<status>"
    Then the search should be rejected with status code <code>

    Examples:
      | status  | code |
      | unknown | 400  |
