INSERT INTO categories (name)
VALUES ('Fruits'),
       ('Vegetables'),
       ('Dairy'),
       ('Bakery'),
       ('Beverages'),
       ('Snacks');

INSERT INTO products (name, price, description, category_id)
VALUES ('Bananas', 1.20, 'Fresh yellow bananas imported from Ecuador.', 1),
       ('Apples', 2.50, 'Crisp red apples packed with flavor and nutrition.', 1),
       ('Tomatoes', 1.75, 'Juicy ripe tomatoes perfect for salads and cooking.', 2),
       ('Spinach', 1.10, 'Organic baby spinach leaves.', 2),
       ('Whole Milk', 3.00, 'Creamy whole milk from local dairy farms.', 3),
       ('Cheddar Cheese', 4.75, 'Aged cheddar cheese with rich flavor.', 3),
       ('White Bread', 2.20, 'Soft white bread baked fresh every morning.', 4),
       ('Chocolate Cake', 5.90, 'Decadent chocolate cake slice with fudge topping.', 4),
       ('Orange Juice', 3.50, '100% pure orange juice with no added sugar.', 5),
       ('Potato Chips', 2.00, 'Crunchy salted potato chips in a family-size pack.', 6);

